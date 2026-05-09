package com.example.app1

import android.Manifest
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.launch

class HomePageFragment : Fragment() {

    private lateinit var viewPager: ViewPager2
    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var autoScrollHandler: Handler
    private lateinit var autoScrollRunnable: Runnable
    private lateinit var weatherCard: View
    private lateinit var weatherTempText: TextView
    private lateinit var weatherPlaceText: TextView
    private lateinit var weatherDetailsText: TextView

    private val autoScrollInterval = 3000L
    private var isWeatherExpanded = false

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        loadWeather()
    }

    private val imageList = listOf(
        R.drawable.img_first,
        R.drawable.img_first_var2,
        R.drawable.img_first_var3,
        R.drawable.img_first_var4
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home_page, container, false)

        viewPager = view.findViewById(R.id.imagePager)
        btnPrev = view.findViewById(R.id.btnPrev)
        btnNext = view.findViewById(R.id.btnNext)
        weatherCard = view.findViewById(R.id.homeWeatherCard)
        weatherTempText = view.findViewById(R.id.homeWeatherTempText)
        weatherPlaceText = view.findViewById(R.id.homeWeatherPlaceText)
        weatherDetailsText = view.findViewById(R.id.homeWeatherDetailsText)

        viewPager.adapter = ImagePagerAdapter(imageList)

        setupAutoScroll()
        setupButtonListeners()
        setupWeatherCard()
        loadWeather()

        return view
    }

    private fun setupWeatherCard() {
        weatherCard.setOnClickListener {
            if (WeatherPreferences.getMode(requireContext()) == WeatherPreferences.MODE_GEO &&
                !WeatherLocationProvider.hasLocationPermission(requireContext())
            ) {
                requestLocationPermission()
            } else {
                isWeatherExpanded = !isWeatherExpanded
                renderWeatherExpandedState()
            }
        }
    }

    private fun loadWeather() {
        if (!WeatherPreferences.isEnabled(requireContext())) {
            weatherCard.visibility = View.GONE
            return
        }

        weatherCard.visibility = View.VISIBLE

        if (WeatherPreferences.getMode(requireContext()) == WeatherPreferences.MODE_GEO &&
            !WeatherLocationProvider.hasLocationPermission(requireContext())
        ) {
            weatherTempText.text = "Погода"
            weatherPlaceText.text = "Нужна геопозиция"
            weatherDetailsText.text = "Нажмите, чтобы разрешить"
            isWeatherExpanded = true
            renderWeatherExpandedState()
            requestLocationPermission()
            return
        }

        weatherTempText.text = "--°C"
        weatherPlaceText.text = "Загрузка..."
        weatherDetailsText.text = ""
        renderWeatherExpandedState()

        lifecycleScope.launch {
            runCatching {
                WeatherRepository.loadCurrentWeather(requireContext())
            }.onSuccess { weather ->
                if (weather == null) {
                    weatherCard.visibility = View.GONE
                } else {
                    weatherCard.visibility = View.VISIBLE
                    weatherTempText.text = weather.temperatureText()
                    weatherPlaceText.text = weather.placeName
                    weatherDetailsText.text = weather.detailsText()
                    renderWeatherExpandedState()
                }
            }.onFailure { error ->
                weatherTempText.text = "Погода"
                weatherPlaceText.text = "Не удалось обновить"
                weatherDetailsText.text = error.message ?: "Проверьте настройки"
                isWeatherExpanded = true
                renderWeatherExpandedState()
            }
        }
    }

    private fun renderWeatherExpandedState() {
        val detailsVisibility = if (isWeatherExpanded) View.VISIBLE else View.GONE
        weatherPlaceText.visibility = detailsVisibility
        weatherDetailsText.visibility = detailsVisibility
    }

    private fun requestLocationPermission() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
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
        if (::weatherCard.isInitialized) {
            loadWeather()
        }
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
