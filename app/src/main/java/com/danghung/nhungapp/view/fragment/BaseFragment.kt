package com.danghung.nhungapp.view.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.R
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
import androidx.lifecycle.ViewModelProvider
import androidx.viewbinding.ViewBinding
import com.danghung.nhungapp.App
import com.danghung.nhungapp.Storage
import com.danghung.nhungapp.view.OnMainCallback
import com.danghung.nhungapp.viewmodel.BaseViewModel

abstract class BaseFragment<V: ViewBinding, M: BaseViewModel> : Fragment(), View.OnClickListener {
    protected lateinit var mContext: Context
    protected lateinit var binding: V

    protected lateinit var viewModel: M
    protected lateinit var callBack: OnMainCallback
    protected var data: Any? = null

    fun setOnCallBack(callBack: OnMainCallback) {
        this.callBack = callBack
    }

    fun setAttachData(data: Any?) {
        this.data = data
    }

    protected fun clearBackStack() {
        requireActivity().supportFragmentManager.popBackStack(null, POP_BACK_STACK_INCLUSIVE)
    }

    protected fun back() {
        requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        this.mContext = context
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = initViewBinding(inflater, container)
        viewModel = ViewModelProvider(this)[getClassVM()]
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
    }

    @SuppressLint("PrivateResource")
    override fun onClick(v: View) {
        v.startAnimation(AnimationUtils.loadAnimation(context, R.anim.abc_fade_in))
        clickView(v)
    }

    protected open fun clickView(v: View){
        //do nothing
    }

    protected fun getStorage(): Storage {
        return App.instance.storage
    }

    abstract fun initViews()

    abstract fun getClassVM(): Class<M>

    abstract fun initViewBinding(inflater: LayoutInflater, container: ViewGroup?): V

    protected fun notify(msg: String?) {
        Toast.makeText(App.instance, msg, Toast.LENGTH_SHORT).show()
    }

    protected fun notify(msg: Int) {
        Toast.makeText(App.instance, msg, Toast.LENGTH_SHORT).show()
    }
}