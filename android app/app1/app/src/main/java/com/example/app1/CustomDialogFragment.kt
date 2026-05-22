package com.example.app1

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment

class CustomDialogFragment : DialogFragment() {

    interface Actions {
        fun onPickFromGallery()
        fun onTakePhoto()
        fun onImportByLink()
    }

    var actions: Actions? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_custom)
        dialog.setCanceledOnTouchOutside(true)

        dialog.findViewById<ViewGroup>(R.id.btnPickPhotoByGallery)?.setOnClickListener {
            dismissAllowingStateLoss()
            actions?.onPickFromGallery()
        }

        dialog.findViewById<ViewGroup>(R.id.btnTakePhoto)?.setOnClickListener {
            dismissAllowingStateLoss()
            actions?.onTakePhoto()
        }

        dialog.findViewById<ViewGroup>(R.id.btnPickPhotoByLink)?.setOnClickListener {
            dismissAllowingStateLoss()
            actions?.onImportByLink()
        }

        return dialog
    }

    override fun onStart() {
        super.onStart()

        dialog?.window?.let { window ->
            val params = window.attributes
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            window.attributes = params
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }
}
