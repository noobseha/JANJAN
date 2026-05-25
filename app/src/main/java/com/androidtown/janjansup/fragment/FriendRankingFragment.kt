package com.androidtown.janjansup.fragment

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.androidtown.janjansup.R
import com.androidtown.janjansup.adapter.FriendRequestAdapter
import com.androidtown.janjansup.adapter.FriendRequestItem
import com.androidtown.janjansup.adapter.RankingAdapter
import com.androidtown.janjansup.databinding.FragmentFriendRankingBinding
import com.androidtown.janjansup.model.RankingModel
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class FriendRankingFragment : Fragment() {

    private var _binding: FragmentFriendRankingBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()

    private val myUid = "user015"
    private var filter = "total"
    private lateinit var friendRankingAdapter: RankingAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFriendRankingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapters()
        setupFilterButtons()
        setupAddFriendButton()
        loadFriendRequests()
        loadFriendRanking()
    }

    private fun setupAdapters() {
        friendRankingAdapter = RankingAdapter { _ -> }
        binding.rvFriendRanking.adapter = friendRankingAdapter
        binding.rvFriendRanking.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun setupFilterButtons() {
        updateFilterButtons("total")

        binding.btnAll.setOnClickListener {
            filter = "total"
            updateFilterButtons("total")
            loadFriendRanking()
        }
        binding.btnSoju.setOnClickListener {
            filter = "soju"
            updateFilterButtons("soju")
            loadFriendRanking()
        }
        binding.btnBeer.setOnClickListener {
            filter = "beer"
            updateFilterButtons("beer")
            loadFriendRanking()
        }
    }

    private fun updateFilterButtons(selected: String) {
        binding.btnAll.setBackgroundResource(
            if (selected == "total") R.drawable.btn_filter_selected else R.drawable.btn_filter_unselected
        )
        binding.btnSoju.setBackgroundResource(
            if (selected == "soju") R.drawable.btn_filter_selected else R.drawable.btn_filter_unselected
        )
        binding.btnBeer.setBackgroundResource(
            if (selected == "beer") R.drawable.btn_filter_selected else R.drawable.btn_filter_unselected
        )
    }

    private fun setupAddFriendButton() {
        binding.btnAddFriend.setOnClickListener {
            showAddFriendDialog()
        }
    }

    private fun loadFriendRequests() {
        val ctx = context ?: return
        db.collection("friendRequests").get()
            .addOnSuccessListener { snapshot ->
                val requests = snapshot.documents.filter {
                    it.getString("toUid") == myUid &&
                            it.getString("status") == "pending"
                }.mapNotNull { doc ->
                    val fromUid = doc.getString("fromUid") ?: return@mapNotNull null
                    val fromNickname = doc.getString("fromNickname") ?: ""
                    val fromUserId = doc.getString("fromUserId") ?: ""
                    FriendRequestItem(
                        requestId = doc.id,
                        fromUid = fromUid,
                        fromNickname = fromNickname,
                        fromUserId = fromUserId
                    )
                }

                if (_binding == null) return@addOnSuccessListener

                if (requests.isEmpty()) {
                    binding.tvFriendRequestCount.visibility = View.GONE
                    binding.layoutFriendRequests.removeAllViews()
                } else {
                    binding.tvFriendRequestCount.visibility = View.VISIBLE
                    binding.tvFriendRequestCount.text = "받은 친구 요청 (${requests.size})"
                    binding.layoutFriendRequests.removeAllViews()
                    requests.forEach { item ->
                        val itemView = LayoutInflater.from(ctx)
                            .inflate(R.layout.item_friend_request, binding.layoutFriendRequests, false)
                        itemView.findViewById<android.widget.TextView>(R.id.tvNickname).text = item.fromNickname
                        itemView.findViewById<android.widget.TextView>(R.id.tvProfileInitial).text =
                            item.fromNickname.firstOrNull()?.toString() ?: "?"
                        itemView.findViewById<android.widget.Button>(R.id.btnAccept).setOnClickListener {
                            acceptFriendRequest(item)
                        }
                        itemView.findViewById<android.widget.Button>(R.id.btnReject).setOnClickListener {
                            rejectFriendRequest(item)
                        }
                        binding.layoutFriendRequests.addView(itemView)
                    }
                }
            }
    }

    private fun loadFriendRanking() {
        db.collection("juseop_users").document(myUid).get()
            .addOnSuccessListener { myDoc ->
                if (_binding == null) return@addOnSuccessListener

                val friendUids = (myDoc.get("friends") as? List<*>)
                    ?.filterIsInstance<String>() ?: emptyList()

                val myNickname = myDoc.getString("nickname") ?: ""
                binding.tvMyNickname.text = "$myNickname (나)"
                binding.tvMyProfileInitial.text = myNickname.firstOrNull()?.toString() ?: "?"
                binding.tvFriendCount.text = "내 친구 (${friendUids.size}명)"

                if (friendUids.isEmpty()) {
                    friendRankingAdapter.submitList(emptyList())
                    binding.tvMyRank.text = "#-"
                    binding.tvMyCups.text = "0잔"
                    return@addOnSuccessListener
                }

                val allUids = friendUids + myUid
                db.collection("juseop").get()
                    .addOnSuccessListener { snapshot ->
                        if (_binding == null) return@addOnSuccessListener

                        val list = snapshot.documents
                            .filter { it.getString("uid") in allUids }
                            .mapNotNull { doc ->
                                val uid = doc.getString("uid") ?: return@mapNotNull null
                                val soju = (doc.getLong("weeklySoju") ?: 0L).toDouble()
                                val beer = (doc.getLong("weeklyBeer") ?: 0L).toDouble()
                                val count = when (filter) {
                                    "soju" -> soju
                                    "beer" -> beer
                                    else -> soju + beer
                                }
                                RankingModel(
                                    userId = uid,
                                    userName = doc.getString("nickname") ?: "",
                                    totalDrinks = count,
                                    sojuCount = soju,
                                    beerCount = beer
                                )
                            }
                            .sortedByDescending { it.totalDrinks }
                            .mapIndexed { index, model -> model.copy(rank = index + 1) }

                        val myData = list.find { it.userId == myUid }
                        binding.tvMyRank.text = "#${myData?.rank ?: "-"}"
                        binding.tvMyCups.text = "${myData?.totalDrinks?.toInt() ?: 0}잔"
                        friendRankingAdapter.submitList(list.filter { it.userId != myUid })
                    }
            }
    }

    private fun showAddFriendDialog() {
        val ctx = context ?: return
        val editText = EditText(ctx).apply {
            hint = "상대방 아이디를 입력하세요"
            setPadding(40, 20, 40, 20)
        }

        AlertDialog.Builder(ctx)
            .setTitle("친구 추가")
            .setView(editText)
            .setPositiveButton("요청 보내기") { _, _ ->
                val inputId = editText.text.toString().trim()
                if (inputId.isNotEmpty()) {
                    sendFriendRequest(inputId, ctx)
                } else {
                    Toast.makeText(ctx, "아이디를 입력해주세요", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun sendFriendRequest(targetUserId: String, ctx: Context) {
        db.collection("juseop_users").get()
            .addOnSuccessListener { snapshot ->
                val targetDoc = snapshot.documents.firstOrNull {
                    it.getString("userId") == targetUserId
                }

                if (targetDoc == null) {
                    Toast.makeText(ctx, "존재하지 않는 아이디입니다", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val targetUid = targetDoc.getString("uid") ?: return@addOnSuccessListener
                val targetNickname = targetDoc.getString("nickname") ?: ""

                if (targetUid == myUid) {
                    Toast.makeText(ctx, "자신에게 친구 요청을 보낼 수 없어요", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                db.collection("juseop_users").document(myUid).get()
                    .addOnSuccessListener { myDoc ->
                        val myNickname = myDoc.getString("nickname") ?: ""
                        val myUserId = myDoc.getString("userId") ?: ""

                        db.collection("friendRequests").get()
                            .addOnSuccessListener { requests ->
                                val alreadySent = requests.documents.any {
                                    it.getString("fromUid") == myUid &&
                                            it.getString("toUid") == targetUid &&
                                            it.getString("status") == "pending"
                                }

                                if (alreadySent) {
                                    Toast.makeText(ctx, "이미 친구 요청을 보냈어요", Toast.LENGTH_SHORT).show()
                                    return@addOnSuccessListener
                                }

                                val request = hashMapOf(
                                    "fromUid" to myUid,
                                    "fromNickname" to myNickname,
                                    "fromUserId" to myUserId,
                                    "toUid" to targetUid,
                                    "toNickname" to targetNickname,
                                    "status" to "pending",
                                    "createdAt" to System.currentTimeMillis()
                                )
                                db.collection("friendRequests").add(request)
                                    .addOnSuccessListener {
                                        Toast.makeText(ctx, "$targetNickname 님에게 친구 요청을 보냈어요!", Toast.LENGTH_SHORT).show()
                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(ctx, "요청 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(ctx, "중복 확인 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(ctx, "내 정보 조회 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(ctx, "유저 조회 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun acceptFriendRequest(item: FriendRequestItem) {
        val ctx = context ?: return
        val batch = db.batch()
        val requestRef = db.collection("friendRequests").document(item.requestId)
        batch.update(requestRef, "status", "accepted")
        val myRef = db.collection("juseop_users").document(myUid)
        val fromRef = db.collection("juseop_users").document(item.fromUid)
        batch.update(myRef, "friends", FieldValue.arrayUnion(item.fromUid))
        batch.update(fromRef, "friends", FieldValue.arrayUnion(myUid))
        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(ctx, "${item.fromNickname} 님과 친구가 됐어요!", Toast.LENGTH_SHORT).show()
                loadFriendRequests()
                loadFriendRanking()
            }
            .addOnFailureListener { e ->
                Toast.makeText(ctx, "수락 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun rejectFriendRequest(item: FriendRequestItem) {
        val ctx = context ?: return
        db.collection("friendRequests").document(item.requestId)
            .update("status", "rejected")
            .addOnSuccessListener {
                Toast.makeText(ctx, "친구 요청을 거절했어요", Toast.LENGTH_SHORT).show()
                loadFriendRequests()
            }
            .addOnFailureListener { e ->
                Toast.makeText(ctx, "거절 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}