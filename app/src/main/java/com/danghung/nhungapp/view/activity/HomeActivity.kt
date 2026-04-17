package com.danghung.nhungapp.view.activity

import androidx.fragment.app.Fragment
import com.danghung.nhungapp.R
import com.danghung.nhungapp.databinding.HomeActivityBinding
import com.danghung.nhungapp.view.fragment.HistoryFragment
import com.danghung.nhungapp.view.fragment.HomeFragment
import com.danghung.nhungapp.viewmodel.CommomVM

@Suppress("DEPRECATION")
class HomeActivity : BaseActivity<HomeActivityBinding, CommomVM>() {

    private val homeFragment = HomeFragment()
    private val historyFragment = HistoryFragment()
    private var activeFragment: Fragment = homeFragment

    override fun getClassVM(): Class<CommomVM> = CommomVM::class.java

    override fun initViewBinding(): HomeActivityBinding =
        HomeActivityBinding.inflate(layoutInflater)

    override fun initViews() {
        supportFragmentManager.beginTransaction().apply {
            add(R.id.fr_frg_main, historyFragment, HistoryFragment.TAG).hide(historyFragment)
            add(R.id.fr_frg_main, homeFragment, HomeFragment.TAG)
            commit()
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    supportFragmentManager.beginTransaction().hide(activeFragment).show(homeFragment).commit()
                    activeFragment = homeFragment
                    true
                }
                R.id.nav_history -> {
                    supportFragmentManager.beginTransaction().hide(activeFragment).show(historyFragment).commit()
                    activeFragment = historyFragment
                    true
                }
                else -> false
            }
        }
    }
}