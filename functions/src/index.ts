import * as admin from "firebase-admin";
import * as functions from "firebase-functions/v1";

admin.initializeApp();

const db = admin.firestore();

const EVENT_TYPE_POUR = "pour";
const EVENT_TYPE_COLOR_GLASS_PROXIMITY = "colorGlassProximity";
const POUR_THRESHOLD_MS = 3000;
const COLOR_GLASS_MAPPING_THRESHOLD_MS = 5000;
const CV_POUR_THRESHOLD_MS = 3000;
const CV_MAX_TRACK_GAP_MS = 1500;
const CV_NEAR_DISTANCE_RATIO = 0.16;
const CV_NEAR_DISTANCE_PX = 140;
const DEFAULT_DRINK_TYPE = "soju";
const KST_OFFSET_MS = 9 * 60 * 60 * 1000;
const COLLECTION_DRINK_DAILY_STATS = "drinkDailyStats";
const COLLECTION_DRINK_STORE_DAILY_STATS = "drinkStoreDailyStats";
const COLLECTION_RANKING_CONTRIBUTIONS = "rankingContributions";
const YOLO_COLOR_SCREEN_TYPES = new Set([
  "phone_screen",
  "smartphone_screen",
  "color_screen",
  "screen_color",
  "user_color_screen",
]);
const YOLO_SOJU_GLASS_TYPES = new Set([
  "soju_glass",
  "sojuglass",
  "shot_glass",
  "green_soju_glass",
]);
const YOLO_BEER_GLASS_TYPES = new Set([
  "beer_glass",
  "beerglass",
  "beer_cup",
]);
const YOLO_SOJU_BOTTLE_TYPES = new Set([
  "soju",
  "soju_bottle",
  "green_soju_bottle",
  "green_bottle",
]);
const YOLO_BEER_BOTTLE_TYPES = new Set([
  "beer",
  "beer_bottle",
  "brown_beer_bottle",
  "clear_beer_bottle",
  "transparent_beer_bottle",
]);

type DrinkType = "soju" | "beer";
type PairType = "colorGlass" | "pour";

type CvDetection = {
  trackId: string;
  objectType: string;
  centerX: number;
  centerY: number;
  x?: number;
  y?: number;
  width?: number;
  height?: number;
  confidence: number;
  screenColorHex?: string;
  physicalGlassId?: string;
  drinkType?: DrinkType;
};

type CvPairUpdateResult = {
  shouldFire: boolean;
  durationMs: number;
};

export const onDetectionUpdate = functions.firestore
  .document("sessions/{sessionId}/detectionEvents/{eventId}")
  .onUpdate(async (change, context) => {
    const before = change.before.data() as Record<string, unknown>;
    const after = change.after.data() as Record<string, unknown>;

    if (before.releasedAt != null || after.releasedAt == null) {
      return null;
    }

    const detectedAtMs = toMillis(after.detectedAt);
    const releasedAtMs = toMillis(after.releasedAt);
    if (detectedAtMs == null || releasedAtMs == null) {
      return null;
    }

    const durationMs = releasedAtMs - detectedAtMs;
    if (durationMs < 0) {
      return null;
    }

    const { sessionId, eventId } = context.params;
    const eventType = typeof after.eventType === "string"
      ? after.eventType
      : EVENT_TYPE_POUR;

    if (eventType === EVENT_TYPE_COLOR_GLASS_PROXIMITY) {
      await handleColorGlassProximity(sessionId, eventId, after, durationMs);
      return null;
    }

    if (eventType === EVENT_TYPE_POUR) {
      await handlePourDetection(sessionId, eventId, after, durationMs);
      return null;
    }

    return null;
  });

export const onCvFrameCreate = functions.firestore
  .document("sessions/{sessionId}/cvFrames/{frameId}")
  .onCreate(async (snap, context) => {
    const frameData = snap.data() as Record<string, unknown>;
    const { sessionId, frameId } = context.params;
    await handleCvFrame(sessionId, frameId, frameData);
    return null;
  });

export const onSessionClosed = functions.firestore
  .document("sessions/{sessionId}")
  .onUpdate(async (change, context) => {
    const before = change.before.data() as Record<string, unknown>;
    const after = change.after.data() as Record<string, unknown>;
    const endedAtMs = toMillis(after.endedAt);
    const wasAlreadyClosed = before.status === "closed" && toMillis(before.endedAt) != null;

    if (after.status !== "closed" || endedAtMs == null || wasAlreadyClosed) {
      return null;
    }

    await aggregateClosedSession(context.params.sessionId, after, endedAtMs);
    return null;
  });

function toMillis(value: unknown): number | null {
  if (value == null || typeof value !== "object") {
    return null;
  }

  const maybeTimestamp = value as { toMillis?: () => number };
  if (typeof maybeTimestamp.toMillis !== "function") {
    return null;
  }

  const millis = maybeTimestamp.toMillis();
  return Number.isFinite(millis) ? millis : null;
}

function normalizeString(value: unknown): string | null {
  if (typeof value !== "string") {
    return null;
  }

  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

function normalizeHex(value: unknown): string | null {
  const raw = normalizeString(value);
  if (raw == null) {
    return null;
  }

  const withHash = raw.startsWith("#") ? raw : `#${raw}`;
  if (!/^#[0-9a-fA-F]{6}$/.test(withHash)) {
    return null;
  }

  return withHash.toLowerCase();
}

function normalizeDrinkType(value: unknown): DrinkType {
  return value === "beer" ? "beer" : DEFAULT_DRINK_TYPE;
}

function normalizeDrinkTypeOrNull(value: unknown): DrinkType | null {
  if (value === "soju" || value === "beer") {
    return value;
  }
  return null;
}

function numberValue(value: unknown): number | null {
  if (typeof value !== "number" || !Number.isFinite(value)) {
    return null;
  }
  return value;
}

function nonNegativeInt(value: unknown): number {
  const parsed = numberValue(value);
  if (parsed == null) {
    return 0;
  }
  return Math.max(0, Math.trunc(parsed));
}

function boundedNumberValue(value: unknown, min: number, max: number): number | null {
  const parsed = numberValue(value);
  if (parsed == null) {
    return null;
  }
  return Math.min(max, Math.max(min, parsed));
}

function sanitizeDocId(value: string): string {
  return value
    .trim()
    .replace(/[^A-Za-z0-9_-]/g, "_")
    .slice(0, 140) || "unknown";
}

function dateKeyFromKstMillis(millis: number): string {
  return new Date(millis + KST_OFFSET_MS).toISOString().slice(0, 10);
}

async function aggregateClosedSession(
  sessionId: string,
  sessionData: Record<string, unknown>,
  endedAtMs: number
): Promise<void> {
  const dateKey = dateKeyFromKstMillis(endedAtMs);
  const storeId = normalizeString(sessionData.storeId) ?? "unknown_store";
  const storeName = normalizeString(sessionData.storeName) ?? "알 수 없는 가게";
  const participants = await db.collection(`sessions/${sessionId}/participants`).get();

  for (const participant of participants.docs) {
    await aggregateParticipantDrinkStats(
      sessionId,
      dateKey,
      storeId,
      storeName,
      endedAtMs,
      participant
    );
  }
}

async function aggregateParticipantDrinkStats(
  sessionId: string,
  dateKey: string,
  storeId: string,
  storeName: string,
  endedAtMs: number,
  participant: admin.firestore.QueryDocumentSnapshot
): Promise<void> {
  const participantData = participant.data() as Record<string, unknown>;
  const userId = normalizeString(participantData.userId) ?? participant.id;
  const userName = normalizeString(participantData.userName)
    ?? normalizeString(participantData.nickname)
    ?? `사용자${userId.slice(-4)}`;
  const imageUrl = normalizeString(participantData.imageUrl) ?? "";
  const sojuCount = nonNegativeInt(participantData.sojuDrinkCount);
  const beerCount = nonNegativeInt(participantData.beerDrinkCount);
  const totalCount = sojuCount + beerCount;

  if (totalCount <= 0) {
    return;
  }

  const contributionId = sanitizeDocId(`${sessionId}_${userId}`);
  const dailyStatId = sanitizeDocId(`${dateKey}_${userId}`);
  const storeStatId = sanitizeDocId(`${dateKey}_${storeId}_${userId}`);
  const contributionRef = db.collection(COLLECTION_RANKING_CONTRIBUTIONS).doc(contributionId);
  const dailyStatRef = db.collection(COLLECTION_DRINK_DAILY_STATS).doc(dailyStatId);
  const storeStatRef = db.collection(COLLECTION_DRINK_STORE_DAILY_STATS).doc(storeStatId);
  const endedAt = admin.firestore.Timestamp.fromMillis(endedAtMs);

  await db.runTransaction(async (transaction: admin.firestore.Transaction) => {
    const contribution = await transaction.get(contributionRef);
    if (contribution.exists) {
      return;
    }

    const countUpdates = {
      sojuCount: admin.firestore.FieldValue.increment(sojuCount),
      beerCount: admin.firestore.FieldValue.increment(beerCount),
      totalCount: admin.firestore.FieldValue.increment(totalCount),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    };

    transaction.set(dailyStatRef, {
      dateKey,
      userId,
      userName,
      imageUrl,
      ...countUpdates,
    }, { merge: true });

    transaction.set(storeStatRef, {
      dateKey,
      storeId,
      storeName,
      userId,
      userName,
      imageUrl,
      ...countUpdates,
    }, { merge: true });

    transaction.set(contributionRef, {
      sessionId,
      participantId: participant.id,
      dateKey,
      storeId,
      storeName,
      userId,
      userName,
      imageUrl,
      sojuCount,
      beerCount,
      totalCount,
      endedAt,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    });
  });
}

async function handleColorGlassProximity(
  sessionId: string,
  eventId: string,
  eventData: Record<string, unknown>,
  durationMs: number
): Promise<void> {
  if (durationMs < COLOR_GLASS_MAPPING_THRESHOLD_MS) {
    return;
  }

  const screenColorHex = normalizeHex(eventData.screenColorHex);
  const physicalGlassId = normalizeString(eventData.physicalGlassId);
  if (screenColorHex == null || physicalGlassId == null) {
    return;
  }

  const drinkType = normalizeDrinkType(eventData.drinkType);
  await mapColorToPhysicalGlass(
    sessionId,
    eventId,
    screenColorHex,
    physicalGlassId,
    drinkType
  );
}

async function mapColorToPhysicalGlass(
  sessionId: string,
  eventId: string,
  screenColorHex: string,
  physicalGlassId: string,
  drinkType: DrinkType
): Promise<void> {
  const participantCollection = db.collection(`sessions/${sessionId}/participants`);
  const colorCandidates = Array.from(new Set([
    screenColorHex,
    screenColorHex.toUpperCase(),
  ]));
  const participants = await participantCollection
    .where("glassColor", "in", colorCandidates)
    .limit(1)
    .get();

  if (participants.empty) {
    return;
  }

  const participantDoc = participants.docs[0];
  const participantData = participantDoc.data();
  const userId = normalizeString(participantData.userId) ?? participantDoc.id;
  const mappingCollection = db.collection(`sessions/${sessionId}/glassMappings`);
  const [targetMappings, duplicateGlassMappings] = await Promise.all([
    mappingCollection
      .where("userId", "==", userId)
      .where("drinkType", "==", drinkType)
      .limit(1)
      .get(),
    mappingCollection
      .where("glassId", "==", physicalGlassId)
      .get(),
  ]);

  const targetRef = targetMappings.empty
    ? mappingCollection.doc()
    : targetMappings.docs[0].ref;

  const batch = db.batch();
  duplicateGlassMappings.docs.forEach((doc: admin.firestore.QueryDocumentSnapshot) => {
    if (doc.ref.path === targetRef.path) {
      return;
    }

    batch.update(doc.ref, {
      glassId: `glass_unmapped_${doc.id}`,
      mappingSource: "colorProximityReplaced",
      remappedAt: admin.firestore.FieldValue.serverTimestamp(),
    });
  });

  const mappingPayload = {
    userId,
    glassId: physicalGlassId,
    drinkType,
    mappingSource: "colorProximity",
    screenColorHex,
    colorGlassEventId: eventId,
    mappedAt: admin.firestore.FieldValue.serverTimestamp(),
  };

  if (targetMappings.empty) {
    batch.set(targetRef, {
      ...mappingPayload,
      drinkCount: 0,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    });
  } else {
    batch.update(targetRef, mappingPayload);
  }

  batch.update(participantDoc.ref, {
    physicalGlassId,
    glassMappingType: "colorProximity",
    mappedScreenColorHex: screenColorHex,
    glassMappedAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  await batch.commit();
}

async function handlePourDetection(
  sessionId: string,
  eventId: string,
  eventData: Record<string, unknown>,
  durationMs: number
): Promise<void> {
  if (durationMs < POUR_THRESHOLD_MS) {
    return;
  }

  const glassId = normalizeString(eventData.glassId)
    ?? normalizeString(eventData.physicalGlassId);
  if (glassId == null) {
    return;
  }

  await incrementDrinkForGlass(
    sessionId,
    eventId,
    glassId,
    durationMs,
    normalizeDrinkTypeOrNull(eventData.drinkType)
  );
}

async function incrementDrinkForGlass(
  sessionId: string,
  eventId: string,
  glassId: string,
  durationMs: number,
  expectedDrinkType: DrinkType | null
): Promise<void> {
  const mappings = expectedDrinkType == null
    ? await db
      .collection(`sessions/${sessionId}/glassMappings`)
      .where("glassId", "==", glassId)
      .limit(1)
      .get()
    : await db
      .collection(`sessions/${sessionId}/glassMappings`)
      .where("glassId", "==", glassId)
      .where("drinkType", "==", expectedDrinkType)
      .limit(1)
      .get();

  if (mappings.empty) {
    return;
  }

  const mapping = mappings.docs[0];
  const mappingData = mapping.data();
  const userId = mappingData.userId as string | undefined;
  const drinkType = normalizeDrinkTypeOrNull(mappingData.drinkType);
  if (!userId || !drinkType) {
    return;
  }
  if (expectedDrinkType != null && drinkType !== expectedDrinkType) {
    return;
  }

  const participants = await db
    .collection(`sessions/${sessionId}/participants`)
    .where("userId", "==", userId)
    .limit(1)
    .get();

  if (participants.empty) {
    return;
  }

  const participantRef = participants.docs[0].ref;
  const mappingDrinkCountField = drinkType === "soju" ? "sojuDrinkCount" : "beerDrinkCount";
  const zeroOtherMappingCount = drinkType === "soju"
    ? { beerDrinkCount: mappingData.beerDrinkCount ?? 0 }
    : { sojuDrinkCount: mappingData.sojuDrinkCount ?? 0 };
  const participantDrinkCountField = drinkType === "soju" ? "sojuDrinkCount" : "beerDrinkCount";
  const sessionDrinkCountField = drinkType === "soju" ? "totalSojuDrinkCount" : "totalBeerDrinkCount";

  await db.runTransaction(async (transaction: admin.firestore.Transaction) => {
    transaction.update(mapping.ref, {
      drinkCount: admin.firestore.FieldValue.increment(1),
      [mappingDrinkCountField]: admin.firestore.FieldValue.increment(1),
      ...zeroOtherMappingCount,
      lastDrinkAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });
    transaction.update(participantRef, {
      [participantDrinkCountField]: admin.firestore.FieldValue.increment(1),
      lastDrinkType: drinkType,
      lastDrinkAt: admin.firestore.FieldValue.serverTimestamp(),
      lastGlassId: glassId,
      lastDetectionEventId: eventId,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });
    transaction.update(db.collection("sessions").doc(sessionId), {
      [sessionDrinkCountField]: admin.firestore.FieldValue.increment(1),
      lastDrinkAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });
  });
}

async function handleCvFrame(
  sessionId: string,
  frameId: string,
  frameData: Record<string, unknown>
): Promise<void> {
  const cameraId = normalizeString(frameData.cameraId) ?? "unknown_camera";
  const capturedAtMs = toMillis(frameData.capturedAt)
    ?? toMillis(frameData.createdAt)
    ?? Date.now();
  const frameWidth = numberValue(frameData.frameWidth);
  const frameHeight = numberValue(frameData.frameHeight);
  const detections = parseCvDetections(frameData, cameraId, frameWidth, frameHeight);
  const colorMappingThresholdMs = boundedNumberValue(
    frameData.colorMappingThresholdMs,
    500,
    10000
  ) ?? COLOR_GLASS_MAPPING_THRESHOLD_MS;
  const pourThresholdMs = boundedNumberValue(
    frameData.pourThresholdMs,
    500,
    10000
  ) ?? CV_POUR_THRESHOLD_MS;

  if (detections.length === 0) {
    return;
  }

  const colorScreens = detections.filter((detection) =>
    YOLO_COLOR_SCREEN_TYPES.has(detection.objectType) && detection.screenColorHex != null
  );
  const glasses = detections.filter((detection) => detection.drinkType != null);
  const bottles = detections.filter((detection) =>
    YOLO_SOJU_BOTTLE_TYPES.has(detection.objectType)
    || YOLO_BEER_BOTTLE_TYPES.has(detection.objectType)
  );

  const activeColorPairIds = new Set<string>();
  const activePourPairIds = new Set<string>();

  for (const screen of colorScreens) {
    for (const glass of glasses) {
      const distance = distanceBetween(screen, glass, frameData);
      if (!distance.isNear || screen.screenColorHex == null || glass.drinkType == null) {
        continue;
      }

      const physicalGlassId = glass.physicalGlassId ?? `${cameraId}_${glass.trackId}`;
      const pairId = sanitizeDocId(`color_${cameraId}_${screen.trackId}_${glass.trackId}_${glass.drinkType}`);
      activeColorPairIds.add(pairId);

      const state = await updateCvPairState(
        sessionId,
        pairId,
        "colorGlass",
        capturedAtMs,
        colorMappingThresholdMs,
        "mappedAt",
        {
          cameraId,
          frameId,
          screenTrackId: screen.trackId,
          glassTrackId: glass.trackId,
          screenColorHex: screen.screenColorHex,
          physicalGlassId,
          drinkType: glass.drinkType,
          distance: distance.value,
          distanceThreshold: distance.threshold,
          confidence: Math.min(screen.confidence, glass.confidence),
        }
      );

      if (state.shouldFire) {
        await mapColorToPhysicalGlass(
          sessionId,
          `cv:${frameId}:${pairId}`,
          screen.screenColorHex,
          physicalGlassId,
          glass.drinkType
        );
      }
    }
  }

  for (const glass of glasses) {
    if (glass.drinkType == null) {
      continue;
    }
    for (const bottle of bottles) {
      const bottleDrinkType = drinkTypeForBottle(bottle);
      if (bottleDrinkType == null || bottleDrinkType !== glass.drinkType) {
        continue;
      }

      const distance = distanceBetween(glass, bottle, frameData);
      if (!distance.isNear) {
        continue;
      }

      const physicalGlassId = glass.physicalGlassId ?? `${cameraId}_${glass.trackId}`;
      const pairId = sanitizeDocId(`pour_${cameraId}_${physicalGlassId}_${bottle.trackId}_${glass.drinkType}`);
      activePourPairIds.add(pairId);

      const state = await updateCvPairState(
        sessionId,
        pairId,
        "pour",
        capturedAtMs,
        pourThresholdMs,
        "countedAt",
        {
          cameraId,
          frameId,
          glassTrackId: glass.trackId,
          bottleTrackId: bottle.trackId,
          physicalGlassId,
          drinkType: glass.drinkType,
          bottleType: bottle.objectType,
          distance: distance.value,
          distanceThreshold: distance.threshold,
          confidence: Math.min(glass.confidence, bottle.confidence),
        }
      );

      if (state.shouldFire) {
        await incrementDrinkForGlass(
          sessionId,
          `cv:${frameId}:${pairId}`,
          physicalGlassId,
          state.durationMs,
          glass.drinkType
        );
      }
    }
  }

  await markInactiveMissingPairs(sessionId, cameraId, "colorGlass", activeColorPairIds, capturedAtMs);
  await markInactiveMissingPairs(sessionId, cameraId, "pour", activePourPairIds, capturedAtMs);
}

function parseCvDetections(
  frameData: Record<string, unknown>,
  cameraId: string,
  frameWidth: number | null,
  frameHeight: number | null
): CvDetection[] {
  const rawDetections = Array.isArray(frameData.detections) ? frameData.detections : [];
  return rawDetections.flatMap((raw, index) => {
    if (raw == null || typeof raw !== "object") {
      return [];
    }

    const data = raw as Record<string, unknown>;
    const objectType = normalizeObjectType(
      normalizeString(data.objectType)
      ?? normalizeString(data.className)
      ?? normalizeString(data.label)
      ?? normalizeString(data.type)
    );
    if (objectType == null) {
      return [];
    }

    const center = resolveDetectionCenter(data, frameWidth, frameHeight);
    if (center == null) {
      return [];
    }

    const trackId = sanitizeDocId(
      normalizeString(data.trackId)
      ?? normalizeString(data.objectId)
      ?? normalizeString(data.id)
      ?? `${objectType}_${index}`
    );
    const screenColorHex = normalizeHex(
      data.screenColorHex ?? data.colorHex ?? data.assignedColorHex
    ) ?? undefined;
    const glassDrinkType = drinkTypeForGlassObjectType(objectType);
    const x = numberValue(data.x);
    const y = numberValue(data.y);
    const width = numberValue(data.width) ?? numberValue(data.w);
    const height = numberValue(data.height) ?? numberValue(data.h);

    return [{
      trackId,
      objectType,
      centerX: center.x,
      centerY: center.y,
      x: x ?? undefined,
      y: y ?? undefined,
      width: width ?? undefined,
      height: height ?? undefined,
      confidence: numberValue(data.confidence) ?? 1,
      screenColorHex,
      physicalGlassId: normalizeString(data.physicalGlassId)
        ?? normalizeString(data.glassId)
        ?? (glassDrinkType == null ? undefined : `${cameraId}_${trackId}`),
      drinkType: glassDrinkType ?? undefined,
    }];
  });
}

function normalizeObjectType(value: string | null): string | null {
  if (value == null) {
    return null;
  }
  return value.trim().toLowerCase().replace(/[\s-]+/g, "_");
}

function resolveDetectionCenter(
  data: Record<string, unknown>,
  frameWidth: number | null,
  frameHeight: number | null
): { x: number; y: number } | null {
  const centerX = numberValue(data.centerX) ?? numberValue(data.cx);
  const centerY = numberValue(data.centerY) ?? numberValue(data.cy);
  if (centerX != null && centerY != null) {
    return normalizePoint(centerX, centerY, frameWidth, frameHeight);
  }

  const x = numberValue(data.x);
  const y = numberValue(data.y);
  const width = numberValue(data.width) ?? numberValue(data.w);
  const height = numberValue(data.height) ?? numberValue(data.h);
  if (x == null || y == null || width == null || height == null) {
    return null;
  }
  return normalizePoint(x + width / 2, y + height / 2, frameWidth, frameHeight);
}

function normalizePoint(
  x: number,
  y: number,
  frameWidth: number | null,
  frameHeight: number | null
): { x: number; y: number } {
  const normalizedX = frameWidth != null && frameWidth > 1 && Math.abs(x) > 1
    ? x / frameWidth
    : x;
  const normalizedY = frameHeight != null && frameHeight > 1 && Math.abs(y) > 1
    ? y / frameHeight
    : y;
  return { x: normalizedX, y: normalizedY };
}

function drinkTypeForGlassObjectType(objectType: string): DrinkType | null {
  if (YOLO_SOJU_GLASS_TYPES.has(objectType)) {
    return "soju";
  }
  if (YOLO_BEER_GLASS_TYPES.has(objectType)) {
    return "beer";
  }
  return null;
}

function drinkTypeForBottle(detection: CvDetection): DrinkType | null {
  if (YOLO_SOJU_BOTTLE_TYPES.has(detection.objectType)) {
    return "soju";
  }
  if (YOLO_BEER_BOTTLE_TYPES.has(detection.objectType)) {
    return "beer";
  }
  return null;
}

function distanceBetween(
  first: CvDetection,
  second: CvDetection,
  frameData: Record<string, unknown>
): { value: number; threshold: number; isNear: boolean } {
  const dx = first.centerX - second.centerX;
  const dy = first.centerY - second.centerY;
  const value = Math.hypot(dx, dy);
  const manualThreshold = numberValue(frameData.nearDistanceThreshold)
    ?? numberValue(frameData.proximityThreshold);
  const threshold = manualThreshold
    ?? (Math.abs(first.centerX) <= 1.2
      && Math.abs(first.centerY) <= 1.2
      && Math.abs(second.centerX) <= 1.2
      && Math.abs(second.centerY) <= 1.2
      ? CV_NEAR_DISTANCE_RATIO
      : CV_NEAR_DISTANCE_PX);
  return { value, threshold, isNear: value <= threshold };
}

async function updateCvPairState(
  sessionId: string,
  pairId: string,
  pairType: PairType,
  seenAtMs: number,
  thresholdMs: number,
  terminalField: "mappedAt" | "countedAt",
  payload: Record<string, unknown>
): Promise<CvPairUpdateResult> {
  const ref = db.collection(`sessions/${sessionId}/cvPairStates`).doc(pairId);
  const snap = await ref.get();
  const data = snap.exists ? (snap.data() as Record<string, unknown>) : {};
  const previousFirstSeenMs = toMillis(data.firstSeenAt);
  const previousLastSeenMs = toMillis(data.lastSeenAt);
  const terminalMs = toMillis(data[terminalField]);
  const wasInactive = data.isActive === false;
  const gapTooLong = previousLastSeenMs == null
    || seenAtMs - previousLastSeenMs > CV_MAX_TRACK_GAP_MS;
  const shouldReset = !snap.exists || wasInactive || gapTooLong;
  const firstSeenMs = shouldReset || previousFirstSeenMs == null
    ? seenAtMs
    : previousFirstSeenMs;
  const durationMs = Math.max(0, seenAtMs - firstSeenMs);
  const shouldFire = terminalMs == null && durationMs >= thresholdMs;
  const seenAt = admin.firestore.Timestamp.fromMillis(seenAtMs);
  const firstSeenAt = admin.firestore.Timestamp.fromMillis(firstSeenMs);
  const updateData: Record<string, unknown> = {
    ...payload,
    pairType,
    firstSeenAt,
    lastSeenAt: seenAt,
    isActive: true,
    durationMs,
    thresholdMs,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  };

  if (shouldReset) {
    updateData[terminalField] = admin.firestore.FieldValue.delete();
  }
  if (shouldFire) {
    updateData[terminalField] = seenAt;
  }

  await ref.set(updateData, { merge: true });
  return { shouldFire, durationMs };
}

async function markInactiveMissingPairs(
  sessionId: string,
  cameraId: string,
  pairType: PairType,
  activePairIds: Set<string>,
  seenAtMs: number
): Promise<void> {
  const snap = await db.collection(`sessions/${sessionId}/cvPairStates`)
    .where("cameraId", "==", cameraId)
    .where("pairType", "==", pairType)
    .where("isActive", "==", true)
    .limit(100)
    .get();
  if (snap.empty) {
    return;
  }

  const seenAt = admin.firestore.Timestamp.fromMillis(seenAtMs);
  const batch = db.batch();
  let hasUpdates = false;
  snap.docs.forEach((doc: admin.firestore.QueryDocumentSnapshot) => {
    if (activePairIds.has(doc.id)) {
      return;
    }
    batch.update(doc.ref, {
      isActive: false,
      lastMissingAt: seenAt,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });
    hasUpdates = true;
  });

  if (hasUpdates) {
    await batch.commit();
  }
}
