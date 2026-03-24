package com.example.app1

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import android.widget.ImageButton
import android.widget.ImageView

class HomePageFragment : Fragment() {

    private lateinit var viewPager: ViewPager2
    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var autoScrollHandler: Handler
    private lateinit var autoScrollRunnable: Runnable
    private val autoScrollInterval = 3000L // 3 секунды


    private val imageList = listOf(
        R.drawable.img_first,
        R.drawable.img_first_var2,
        R.drawable.img_first_var3,
        R.drawable.img_first_var4
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_home_page, container, false)


        viewPager = view.findViewById(R.id.imagePager)


        btnPrev = view.findViewById(R.id.btnPrev)
        btnNext = view.findViewById(R.id.btnNext)


        val adapter = ImagePagerAdapter(imageList)
        viewPager.adapter = adapter

        setupAutoScroll()

        setupButtonListeners()

        return view
    }

    private fun setupAutoScroll() {
        autoScrollHandler = Handler(Looper.getMainLooper())

        autoScrollRunnable = object : Runnable {
            override fun run() {
                val currentItem = viewPager.currentItem
                val itemCount = imageList.size

                if (itemCount == 0) return

                val nextItem = if (currentItem == itemCount - 1) 0 else currentItem + 1
                viewPager.setCurrentItem(nextItem, true)

                autoScrollHandler.postDelayed(this, autoScrollInterval)
            }
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                autoScrollHandler.removeCallbacks(autoScrollRunnable)
                autoScrollHandler.postDelayed(autoScrollRunnable, autoScrollInterval)
            }
        })
    }

    private fun setupButtonListeners() {
        btnPrev.setOnClickListener {
            val currentItem = viewPager.currentItem
            val itemCount = imageList.size

            if (itemCount > 0) {
                val prevItem = if (currentItem == 0) itemCount - 1 else currentItem - 1
                viewPager.setCurrentItem(prevItem, true)
            }

            autoScrollHandler.removeCallbacks(autoScrollRunnable)
            setupAutoScroll()
            autoScrollHandler.postDelayed(autoScrollRunnable, autoScrollInterval)
        }

        btnNext.setOnClickListener {
            val currentItem = viewPager.currentItem
            val itemCount = imageList.size

            if (itemCount > 0) {
                val nextItem = if (currentItem == itemCount - 1) 0 else currentItem + 1
                viewPager.setCurrentItem(nextItem, true)
            }

            autoScrollHandler.removeCallbacks(autoScrollRunnable)
            setupAutoScroll()
            autoScrollHandler.postDelayed(autoScrollRunnable, autoScrollInterval)
        }
    }

    override fun onResume() {
        super.onResume()
        autoScrollHandler.postDelayed(autoScrollRunnable, autoScrollInterval)
    }

    override fun onPause() {
        super.onPause()
        autoScrollHandler.removeCallbacks(autoScrollRunnable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        autoScrollHandler.removeCallbacks(autoScrollRunnable)
    }

    inner class ImagePagerAdapter(private val images: List<Int>) :
        RecyclerView.Adapter<ImagePagerAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imageView: ImageView = view.findViewById(R.id.imageView)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.layout_image_home, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.imageView.setImageResource(images[position])
        }

        override fun getItemCount(): Int = images.size
    }
}