package com.danghung.nhungapp.view.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.danghung.nhungapp.App
import com.danghung.nhungapp.R
import com.danghung.nhungapp.data.local.entity.HistoryEntity
import com.danghung.nhungapp.databinding.FragmentHistoryBinding
import com.danghung.nhungapp.databinding.ItemHistoryBinding
import com.danghung.nhungapp.viewmodel.CommomVM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryFragment : BaseFragment<FragmentHistoryBinding, CommomVM>() {

    companion object {
        val TAG: String = HistoryFragment::class.java.name
    }

    private val historyDao by lazy { App.instance.appDatabase.historyDao() }
    private val historyList = mutableListOf<HistoryEntity>()
    private lateinit var adapter: HistoryAdapter

    override fun getClassVM() = CommomVM::class.java

    override fun initViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ) = FragmentHistoryBinding.inflate(inflater, container, false)

    override fun initViews() {
        setupRecycler()
        binding.btnClearHistory.setOnClickListener { confirmClearHistory() }
        loadHistory()
    }

    // Refresh history when returning to this tab
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            loadHistory()
        }
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
    }

    private fun setupRecycler() {
        adapter = HistoryAdapter(historyList)
        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistory.adapter = adapter
    }

    private fun loadHistory() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val list = historyDao.getAllHistory()
            withContext(Dispatchers.Main) {
                if (isAdded) {
                    historyList.clear()
                    historyList.addAll(list)
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun confirmClearHistory() {
        AlertDialog.Builder(requireContext())
            .setTitle("Xóa lịch sử")
            .setMessage("Bạn có chắc chắn muốn xóa toàn bộ lịch sử cho ăn?")
            .setPositiveButton("Xóa") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    historyDao.deleteAll()
                    withContext(Dispatchers.Main) {
                        historyList.clear()
                        adapter.notifyDataSetChanged()
                        notify("Đã xóa lịch sử")
                    }
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    class HistoryAdapter(private val list: List<HistoryEntity>) : RecyclerView.Adapter<HistoryAdapter.VH>() {
        override fun onCreateViewHolder(p: ViewGroup, v: Int): VH {
            return VH(ItemHistoryBinding.inflate(LayoutInflater.from(p.context), p, false))
        }

        override fun onBindViewHolder(h: VH, i: Int) {
            h.bind(list[i])
        }

        override fun getItemCount() = list.size

        class VH(private val b: ItemHistoryBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(item: HistoryEntity) {
                b.tvHistoryType.text = item.type
                b.tvHistoryDateTime.text = item.dateTime
                b.ivIcon.setImageResource(if (item.type == "THỦ CÔNG") R.drawable.ic_an else R.drawable.ic_calendar)
            }
        }
    }
}
