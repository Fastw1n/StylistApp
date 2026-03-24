package com.example.app1

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class GalleryFragment : Fragment(R.layout.fragment_gallery) {

    companion object {
        fun newInstance(images: List<Uri>): GalleryFragment {
            val fragment = GalleryFragment()
            fragment.imageList = images
            return fragment
        }
    }

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {

            }
        }


    private var imageList: List<Uri> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recycler = view.findViewById<RecyclerView>(R.id.recyclerView)
        recycler.layoutManager = GridLayoutManager(requireContext(), 3)
        recycler.adapter = ImageAdapter(imageList)
        view.findViewById<Button>(R.id.btnPickPhotoByGallery)
            .setOnClickListener {
                pickImageLauncher.launch("image/*")
            }
    }
}
