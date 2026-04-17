package com.danghung.nhungapp.view.fragment

import android.app.TimePickerDialog
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.danghung.nhungapp.App
import com.danghung.nhungapp.R
import com.danghung.nhungapp.data.local.dao.ScheduleDao
import com.danghung.nhungapp.data.local.entity.HistoryEntity
import com.danghung.nhungapp.data.local.entity.ScheduleEntity
import com.danghung.nhungapp.databinding.FragmentHomeBinding
import com.danghung.nhungapp.databinding.ItemDateBinding
import com.danghung.nhungapp.service.FeedScheduleAlarmManager
import com.danghung.nhungapp.service.FeedScheduleExecutionEngine
import com.danghung.nhungapp.service.PetFeederMqttClient
import com.danghung.nhungapp.viewmodel.CommomVM
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*

class HomeFragment : BaseFragment<FragmentHomeBinding, CommomVM>() {

    companion object {
        val TAG: String = HomeFragment::class.java.name
        private const val DEFAULT_TIME = "08:00"
        private const val FEED_TIMEOUT = 20000L
        private const val PREF_HOME = "pref_home"
        private const val KEY_AUTO = "auto_sensor"
        private const val KEY_AUTO_START_PROMPTED = "key_auto_start_prompted"
        private const val HOME_CHANNEL_ID = "home_notifications"
        private const val HOME_WELCOME_NOTIFICATION_ID = 1001
    }

    private val schedules = mutableListOf<ScheduleItem>()
    private lateinit var adapter: ScheduleAdapter
    private var selectedScheduleDateTime: String = ""

    private val handler = Handler(Looper.getMainLooper())
    private var isFeedPending = false
    private var isCheckFoodPending = false

    private val hourMinuteFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    private val scheduleDateTimeFormatter =
        DateTimeFormatter.ofPattern("dd/MM/yyyy | HH:mm", Locale.forLanguageTag("vi-VN"))

    private val mqtt by lazy { PetFeederMqttClient(requireContext()) }
    private val dao: ScheduleDao by lazy { App.instance.appDatabase.scheduleDao() }
    private val historyDao by lazy { App.instance.appDatabase.historyDao() }
    private val prefs by lazy {
        requireContext().getSharedPreferences(PREF_HOME, 0)
    }
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                showWelcomeNotification()
            } else {
                notify("Bạn chưa cấp quyền thông báo")
            }
        }
    private val scheduleStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != FeedScheduleExecutionEngine.ACTION_SCHEDULE_UPDATED) return
            loadSchedules()
        }
    }

    override fun getClassVM() = CommomVM::class.java

    override fun initViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ) = FragmentHomeBinding.inflate(inflater, container, false)

    override fun initViews() {
        requestNotificationPermissionIfNeeded()
        requestExactAlarmPermissionIfNeeded()
        requestAutoStartPermissionIfNeeded()
        showWelcomeNotification()

        setupRecycler()
        bindClicks()
        setupMqtt()
        FeedScheduleAlarmManager.rescheduleAllPending(requireContext().applicationContext)
        loadSchedules()

        binding.switchAutoSensor.isChecked = getAutoState()
        updateFoodStatusTimeNow()
        selectedScheduleDateTime = getCurrentDateTimeGmt7()
        binding.tvSelectedSchedule.text = selectedScheduleDateTime
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(FeedScheduleExecutionEngine.ACTION_SCHEDULE_UPDATED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(
                scheduleStatusReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            requireContext().registerReceiver(scheduleStatusReceiver, filter)
        }
    }

    override fun onStop() {
        runCatching { requireContext().unregisterReceiver(scheduleStatusReceiver) }
        super.onStop()
    }

    override fun onDestroyView() {
        handler.removeCallbacksAndMessages(null)
        mqtt.setListener(null)
        mqtt.disconnect()
        super.onDestroyView()
    }

    // ================= MQTT =================

    private fun setupMqtt() {
        mqtt.setListener(object : PetFeederMqttClient.Listener {

            override fun onConnected() {
                if (!isAdded) return

                // sync trạng thái auto
                mqtt.publishAutoSensor(getAutoState())

                // lấy trạng thái food
                mqtt.requestFoodStatus()
            }

            override fun onFoodDistance(distanceCm: Int) {
                if (!isAdded) return
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                    val statusText = renderFood(distanceCm)
                    if (isCheckFoodPending) {
                        isCheckFoodPending = false
                        updateFoodStatusTimeNow()
                        notify("Lượng thức ăn: $statusText")
                    }
                }
            }

            override fun onFeedDoneReport() {
                if (isFeedPending) {
                    isFeedPending = false
                    handler.removeCallbacksAndMessages(null)
                    notify("Cho ăn thành công")
                    
                    val dateTimeStr = java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy | HH:mm:ss", java.util.Locale.forLanguageTag("vi-VN")))
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        historyDao.insertHistory(
                            HistoryEntity(
                                dateTime = dateTimeStr,
                                type = "THỦ CÔNG"
                            )
                        )
                    }
                }
            }

            override fun onAutoSensorStateChanged(enabled: Boolean) {
                if (binding.switchAutoSensor.isChecked != enabled) {
                    binding.switchAutoSensor.setOnCheckedChangeListener(null)
                    binding.switchAutoSensor.isChecked = enabled
                    binding.switchAutoSensor.setOnCheckedChangeListener(autoListener)
                }
                saveAutoState(enabled)
            }

            override fun onError(message: String) {
                if (isFeedPending) {
                    isFeedPending = false
                    handler.removeCallbacksAndMessages(null)
                    notify("Feed lỗi: $message")
                }
            }
        })

        mqtt.connect()
    }

    // ================= UI =================

    private val autoListener = { _: android.widget.CompoundButton, checked: Boolean ->
        saveAutoState(checked)
        mqtt.publishAutoSensor(checked)
        notify(if (checked) "Auto ON" else "Auto OFF")
    }

    private fun bindClicks() {
        binding.btnFeedNow.setOnClickListener { feedNow() }
        binding.btnCheckFood.setOnClickListener { checkFood() }
        binding.btnPickSchedule.setOnClickListener { showDatePicker() }
        binding.btnSaveSchedule.setOnClickListener { saveScheduleFromSelection() }

        binding.switchAutoSensor.setOnCheckedChangeListener(autoListener)
    }

    private fun renderFood(dist: Int): String {
        val text = when {
            dist <= 2 -> "Đầy"
            dist <= 5 -> "Vừa"
            else -> "Hết"
        }
        binding.tvFoodStatus.text = text
        return text
    }

    private fun updateFoodStatusTimeNow() {
        val nowText = LocalTime.now().format(hourMinuteFormatter)
        binding.tvFoodStatusTime.text = "Cập nhật lúc: $nowText"
    }

    // ================= ACTION =================

    private fun feedNow() {
        isFeedPending = true

        mqtt.publishFeedNow()
        notify("Đang cho ăn...")

        handler.postDelayed({
            if (isFeedPending) {
                isFeedPending = false
                notify("Timeout - không phản hồi")
            }
        }, FEED_TIMEOUT)
    }

    private fun checkFood() {
        isCheckFoodPending = true
        mqtt.requestFoodStatus()
        notify("Đang check...")
    }

    // ================= AUTO =================

    private fun saveAutoState(v: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO, v).apply()
    }

    private fun getAutoState(): Boolean {
        return prefs.getBoolean(KEY_AUTO, false)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted = ContextCompat.checkSelfPermission(
            requireContext(),
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun showWelcomeNotification() {
        val context = requireContext().applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        createHomeNotificationChannelIfNeeded(context)
        val notification = NotificationCompat.Builder(context, HOME_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pet)
            .setContentTitle("Pet Feeder")
            .setContentText("Chào mừng bạn đã quay trở lại, cùng nhau chăm sóc boss nhé!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(HOME_WELCOME_NOTIFICATION_ID, notification)
    }

    private fun createHomeNotificationChannelIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(HOME_CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            HOME_CHANNEL_ID,
            "Home Alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Thong bao Home"
        }
        manager.createNotificationChannel(channel)
    }

    private fun requestExactAlarmPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (alarmManager.canScheduleExactAlarms()) return

        notify("Cần cấp quyền báo thức chính xác để lịch chạy ổn khi đóng app")
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${requireContext().packageName}")
        }
        runCatching { startActivity(intent) }
    }

    private fun requestAutoStartPermissionIfNeeded() {
        val prompted = prefs.getBoolean(KEY_AUTO_START_PROMPTED, false)
        if (prompted) return

        val opened = openAutoStartSettings()
        if (opened) {
            prefs.edit().putBoolean(KEY_AUTO_START_PROMPTED, true).apply()
            notify("Vui lòng bật Auto Start cho ứng dụng")
        }
    }

    private fun openAutoStartSettings(): Boolean {
        val context = requireContext()
        val intents = listOf(
            Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            },
            Intent().apply {
                component = ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                )
            },
            Intent().apply {
                component = ComponentName(
                    "com.oppo.safe",
                    "com.oppo.safe.permission.startup.StartupAppListActivity"
                )
            },
            Intent().apply {
                component = ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                )
            },
            Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
            }
        )

        intents.forEach { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val canHandle = intent.resolveActivity(context.packageManager) != null
            if (canHandle) {
                runCatching { startActivity(intent) }
                    .onSuccess { return true }
            }
        }

        return false
    }

    // ================= SCHEDULE =================

    private fun showDatePicker() {
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Chọn ngày cho ăn")
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            val localDate = Instant.ofEpochMilli(selection)
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
            showTimePicker(localDate)
        }

        datePicker.show(parentFragmentManager, "home_date_picker")
    }

    private fun showTimePicker(localDate: LocalDate) {
        val defaultTime = LocalTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))

        val picker = TimePickerDialog(
            requireContext(),
            { _, selectedHour, selectedMinute ->
                val selectedDateTime = LocalDateTime.of(
                    localDate,
                    LocalTime.of(selectedHour, selectedMinute)
                )
                val display = selectedDateTime.format(scheduleDateTimeFormatter)
                selectedScheduleDateTime = display
                binding.tvSelectedSchedule.text = display
                notify("Đã chọn lịch: $display")
            },
            defaultTime.hour,
            defaultTime.minute,
            true
        )
        picker.show()
    }

    private fun saveScheduleFromSelection() {
        val dateTime = selectedScheduleDateTime.trim()
        if (dateTime.isBlank()) {
            notify("Chưa có ngày giờ để lưu")
            return
        }
        if (!isScheduleInFuture(dateTime)) {
            notify("Vui lòng chọn thời gian trong tương lai")
            return
        }

        val appContext = requireContext().applicationContext
        lifecycleScope.launch(Dispatchers.IO) {
            val duplicateCount = dao.countByDateTime(dateTime)
            if (duplicateCount > 0) {
                withContext(Dispatchers.Main) {
                    notify("Lịch đã tồn tại ở phút này")
                }
                return@launch
            }

            val insertedId = dao.insertSchedule(
                ScheduleEntity(dateTime = dateTime, status = false)
            )
            FeedScheduleAlarmManager.schedule(appContext, insertedId, dateTime)

            withContext(Dispatchers.Main) {
                schedules.add(0, ScheduleItem(insertedId, dateTime, false))
                adapter.notifyItemInserted(0)
                binding.rvSchedules.scrollToPosition(0)
                notify("Đã lưu lịch: $dateTime")
            }
        }
    }

    private fun isScheduleInFuture(dateTime: String): Boolean {
        val localDateTime = runCatching {
            LocalDateTime.parse(dateTime, scheduleDateTimeFormatter)
        }.getOrNull() ?: return false

        val scheduleMillis = localDateTime
            .atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
            .toInstant()
            .toEpochMilli()
        return scheduleMillis > System.currentTimeMillis()
    }

    private fun getCurrentDateTimeGmt7(): String {
        return ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).format(scheduleDateTimeFormatter)
    }

    private fun setupRecycler() {
        adapter = ScheduleAdapter(schedules) { item, pos ->
            schedules.removeAt(pos)
            adapter.notifyItemRemoved(pos)
            lifecycleScope.launch(Dispatchers.IO) {
                FeedScheduleAlarmManager.cancel(requireContext().applicationContext, item.id)
                dao.deleteSchedule(
                    ScheduleEntity(item.id, item.dateTime, item.status)
                )
            }
        }

        binding.rvSchedules.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSchedules.adapter = adapter
    }

    private fun loadSchedules() {
        lifecycleScope.launch(Dispatchers.IO) {
            val list = dao.getAllSchedules()
            withContext(Dispatchers.Main) {
                schedules.clear()
                schedules.addAll(list.map {
                    ScheduleItem(it.id, it.dateTime, it.status)
                })
                adapter.notifyDataSetChanged()
            }
        }
    }

    // ================= MODEL =================

    data class ScheduleItem(
        val id: Long,
        val dateTime: String,
        val status: Boolean
    )

    class ScheduleAdapter(
        private val list: List<ScheduleItem>,
        private val onDelete: (ScheduleItem, Int) -> Unit
    ) : RecyclerView.Adapter<ScheduleAdapter.VH>() {

        override fun onCreateViewHolder(p: ViewGroup, v: Int): VH {
            return VH(
                ItemDateBinding.inflate(LayoutInflater.from(p.context), p, false)
            )
        }

        override fun onBindViewHolder(h: VH, i: Int) {
            h.bind(list[i], onDelete)
        }

        override fun getItemCount() = list.size

        class VH(private val b: ItemDateBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(item: ScheduleItem, onDelete: (ScheduleItem, Int) -> Unit) {
                b.tvDateTime.text = item.dateTime
                b.tvEatStatus.text = if (item.status) "Đã ăn" else "Chưa ăn"
                b.tvEatStatus.setBackgroundResource(
                    if (item.status) R.drawable.bg_eat_status_done
                    else R.drawable.bg_eat_status_pending
                )

                b.ivDeleteSchedule.setOnClickListener {
                    onDelete(item, bindingAdapterPosition)
                }
            }
        }
    }
}