package com.danghung.nhungapp.view

interface OnMainCallback {
    fun callBack(key: String, data: Any) {}
    fun showFragment(tag: String, data: Any?, isBacked: Boolean)
}