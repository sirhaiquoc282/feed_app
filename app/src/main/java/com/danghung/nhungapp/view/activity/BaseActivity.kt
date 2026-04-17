package com.danghung.nhungapp.view.activity

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.viewbinding.ViewBinding
import com.danghung.nhungapp.App
import com.danghung.nhungapp.R
import com.danghung.nhungapp.Storage
import com.danghung.nhungapp.view.OnMainCallback
import com.danghung.nhungapp.view.fragment.BaseFragment

@Suppress("DEPRECATION")
abstract class BaseActivity<V : ViewBinding, M : ViewModel> : AppCompatActivity(),
    View.OnClickListener, OnMainCallback {

    protected lateinit var binding: V
    protected lateinit var viewModel: M

    override fun onCreate(data: Bundle?) {
        super.onCreate(data)
        binding = initViewBinding()
        setContentView(binding.root)
        setupStatus()
        setupBackPress()
        viewModel = ViewModelProvider(this)[getClassVM()]
        initViews()

    }

    private fun setupStatus() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        val root = findViewById<View?>(R.id.fr_frg_main)
        if (root != null) {
            val initialPaddingLeft = root.paddingLeft
            val initialPaddingTop = root.paddingTop
            val initialPaddingRight = root.paddingRight
            val initialPaddingBottom = root.paddingBottom
            ViewCompat.setOnApplyWindowInsetsListener(
                root
            ) { v: View?, insets: WindowInsetsCompat? ->
                val systemBarInsets =
                    insets!!.getInsets(WindowInsetsCompat.Type.systemBars())
                v!!.setPadding(
                    initialPaddingLeft + systemBarInsets.left,
                    initialPaddingTop + systemBarInsets.top,
                    initialPaddingRight + systemBarInsets.right,
                    initialPaddingBottom + systemBarInsets.bottom
                )
                insets
            }
            ViewCompat.requestApplyInsets(root)
            window.statusBarColor = getResources().getColor(R.color.white, theme)
            val controller = WindowInsetsControllerCompat(window, root)
            controller.isAppearanceLightStatusBars = true
        }
    }

    private fun setupBackPress() {
        // Đăng ký callback xử lý back
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val count = supportFragmentManager.backStackEntryCount
                if (count == 0) {
                    askForExitApp()
                } else {
                    supportFragmentManager.popBackStack()
                }
            }
        })
    }

    private fun askForExitApp() {
        AlertDialog.Builder(this).setTitle("Alert").setMessage("Close this app?")
            .setPositiveButton("Close") { _, _ ->
                finish()
            }.setNegativeButton("Don't") { dialog, _ ->
                dialog.dismiss()
            }.show()
    }

    abstract fun initViews()

    abstract fun initViewBinding(): V

    abstract fun getClassVM(): Class<M>

    @SuppressLint("PrivateResource")
    override fun onClick(v: View) {
        v.startAnimation(AnimationUtils.loadAnimation(this, androidx.appcompat.R.anim.abc_fade_in))
        clickView(v)
    }

    protected open fun clickView(v: View) {
        //do nothing
    }

    protected fun getStorage(): Storage {
        return App.instance.storage
    }

    protected fun notify(msg: String?) {
        Toast.makeText(App.instance, msg, Toast.LENGTH_SHORT).show()
    }

    protected fun notify(msg: Int) {
        Toast.makeText(App.instance, msg, Toast.LENGTH_SHORT).show()
    }

    override fun showFragment(tag: String, data: Any?, isBacked: Boolean) {
        try {
            val clazz = Class.forName(tag)
            val baseFragment = clazz.getConstructor().newInstance() as BaseFragment<*, *>
            //set callback để gọi về act
            baseFragment.setOnCallBack(this)
            baseFragment.setAttachData(data)
            val trans = supportFragmentManager.beginTransaction()
                .replace(R.id.fr_frg_main, baseFragment, tag)

            if (isBacked) {
                trans.addToBackStack(null)
            }
            trans.commit()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


}