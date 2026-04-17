package com.danghung.nhungapp

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class CommonUtils private constructor(){
    companion object{
        const val PREF_FILE = "pref_saving"
        private var instance: CommonUtils? = null
        fun getInstance(): CommonUtils{
            if (instance == null){
                instance = CommonUtils()
            }
            return instance!!
        }
    }

    fun savePref(key: String, value: String) {
        val pref: SharedPreferences = App.Companion.instance.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        pref.edit { putString(key, value) }
    }

    // Lấy String, nếu không có thì trả về defaultValue
    fun getPref(key: String): String? {
        val pref: SharedPreferences = App.Companion.instance.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        return pref.getString(key, null)
    }

    // Xóa 1 key cụ thể
    fun clearPref(key: String) {
        val pref: SharedPreferences = App.Companion.instance.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        pref.edit { remove(key) }
    }

    // Xóa toàn bộ file SharedPreferences
    fun clearAllPref() {
        val pref: SharedPreferences = App.Companion.instance.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        pref.edit { clear() }
    }
}