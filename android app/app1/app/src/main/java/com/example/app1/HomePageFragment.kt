package com.example.app1

import android.Manifest
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import coil.load
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
    private lateinit var carouselAdapter: HomeCarouselAdapter

    private val autoScrollInterval = 3000L
    private var isWeatherExpanded = false

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        loadWeather()
    }

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

        carouselAdapter = HomeCarouselAdapter(emptyOutfitPages())
        viewPager.adapter = carouselAdapter

        setupAutoScroll()
        setupButtonListeners()
        setupWeatherCard()
        loadWeather()
        loadUserOutfits()

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

    private fun loadUserOutfits() {
        lifecycleScope.launch {
            runCatching {
                RetrofitClient.api.getOutfits().outfits
                    .filter { outfit -> outfit.items.isNotEmpty() }
            }.onSuccess { outfits ->
                if (::carouselAdapter.isInitialized) {
                    val pages = if (outfits.isEmpty()) {
                        emptyOutfitPages()
                    } else {
                        outfits.map { HomeCarouselPage.Outfit(it) } +
                            HomeCarouselPage.CreateOutfit(R.drawable.add_outfit)
                    }

                    carouselAdapter.submitPages(pages)
                    viewPager.setCurrentItem(0, false)
                    restartAutoScroll()
                }
            }
        }
    }

    private fun openOutfit(outfitId: String) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.framelayout, OutfitDetailFragment.newInstance(outfitId))
            .addToBackStack("home_outfit_detail")
            .commit()
    }

    private fun openOutfitCreator() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.framelayout, OutfitCreatorFragment())
            .addToBackStack("home_create_outfit")
            .commit()
    }

    private fun emptyOutfitPages(): List<HomeCarouselPage> =
        listOf(HomeCarouselPage.CreateOutfit(R.drawable.first_outfit))

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
                val itemCount = viewPager.adapter?.itemCount ?: 0

                if (itemCount <= 1) return

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

    private fun restartAutoScroll() {
        autoScrollHandler.removeCallbacks(autoScrollRunnable)
        autoScrollHandler.postDelayed(autoScrollRunnable, autoScrollInterval)
    }

    private fun setupButtonListeners() {
        btnPrev.setOnClickListener {
            val currentItem = viewPager.currentItem
            val itemCount = viewPager.adapter?.itemCount ?: 0

            if (itemCount > 0) {
                val prevItem = if (currentItem == 0) itemCount - 1 else currentItem - 1
                viewPager.setCurrentItem(prevItem, true)
            }

            restartAutoScroll()
        }

        btnNext.setOnClickListener {
            val currentItem = viewPager.currentItem
            val itemCount = viewPager.adapter?.itemCount ?: 0

            if (itemCount > 0) {
                val nextItem = if (currentItem == itemCount - 1) 0 else currentItem + 1
                viewPager.setCurrentItem(nextItem, true)
            }

            restartAutoScroll()
        }
    }

    override fun onResume() {
        super.onResume()
        autoScrollHandler.postDelayed(autoScrollRunnable, autoScrollInterval)
        if (::weatherCard.isInitialized) {
            loadWeather()
        }
        if (::carouselAdapter.isInitialized) {
            loadUserOutfits()
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

    private sealed class HomeCarouselPage {
        data class CreateOutfit(val imageRes: Int) : HomeCarouselPage()
        data class Outfit(val outfit: OutfitDto) : HomeCarouselPage()
    }

    private inner class HomeCarouselAdapter(initialPages: List<HomeCarouselPage>) :
        RecyclerView.Adapter<HomeCarouselAdapter.ViewHolder>() {

        private val pages = initialPages.toMutableList()

        inner class ViewHolder(val container: FrameLayout) : RecyclerView.ViewHolder(container)

        fun submitPages(newPages: List<HomeCarouselPage>) {
            pages.clear()
            pages.addAll(newPages)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val container = FrameLayout(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(resources.getColor(R.color.white, null))
            }
            return ViewHolder(container)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.container.removeAllViews()
            when (val page = pages[position]) {
                is HomeCarouselPage.CreateOutfit -> renderCreateOutfit(holder.container, page.imageRes)
                is HomeCarouselPage.Outfit -> renderOutfit(holder.container, page.outfit)
            }
        }

        override fun getItemCount(): Int = pages.size

        private fun renderCreateOutfit(container: FrameLayout, imageRes: Int) {
            container.isClickable = true
            container.isFocusable = true
            container.contentDescription = "Создать образ"
            container.setOnClickListener {
                openOutfitCreator()
            }
            container.addView(ImageView(container.context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageResource(imageRes)
            })
        }

        private fun renderOutfit(container: FrameLayout, outfit: OutfitDto) {
            val width = resources.displayMetrics.widthPixels
            val collageHeight = 444.dp
            val sortedItems = outfit.items
                .sortedBy { item -> item.previewOrder() }
                .take(6)

            container.isClickable = true
            container.isFocusable = true
            container.contentDescription = outfit.displayName()
            container.setOnClickListener {
                openOutfit(outfit.outfit_id)
            }

            sortedItems.forEachIndexed { index, item ->
                val slot = outfitSlot(index, sortedItems.size)
                container.addView(ImageView(container.context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        (width * slot.widthRatio).toInt(),
                        (collageHeight * slot.heightRatio).toInt()
                    ).apply {
                        leftMargin = (width * slot.leftRatio).toInt()
                        topMargin = (collageHeight * slot.topRatio).toInt()
                        gravity = Gravity.TOP or Gravity.START
                    }
                    adjustViewBounds = true
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    load(ApiUrl.resolveMediaUrl(item.normalized_image_url)) {
                        crossfade(true)
                        placeholder(R.drawable.ic_wardrobe)
                        error(R.drawable.ic_wardrobe)
                    }
                })
            }

            container.addView(TextView(container.context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    56.dp
                ).apply {
                    gravity = Gravity.BOTTOM
                }
                setBackgroundColor(resources.getColor(R.color.white, null))
                gravity = Gravity.CENTER_VERTICAL
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(18.dp, 0, 18.dp, 0)
                text = outfit.displayName()
                setTextColor(resources.getColor(R.color.black, null))
                textSize = 20f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
        }

        private fun OutfitDto.displayName(): String =
            name?.takeIf { it.isNotBlank() } ?: "Образ"

        private fun OutfitItemDto.previewOrder(): Int =
            when (category.lowercase()) {
                "outerwear" -> 0
                "top" -> 1
                "bottom" -> 2
                "shoes" -> 3
                "accessory" -> 4
                else -> 5
            }

        private fun outfitSlot(index: Int, count: Int): OutfitSlotPosition {
            val slots = when (count) {
                1 -> listOf(
                    OutfitSlotPosition(0.12f, 0.06f, 0.76f, 0.88f)
                )
                2 -> listOf(
                    OutfitSlotPosition(0.04f, 0.12f, 0.45f, 0.76f),
                    OutfitSlotPosition(0.51f, 0.12f, 0.45f, 0.76f)
                )
                3 -> listOf(
                    OutfitSlotPosition(0.03f, 0.07f, 0.46f, 0.72f),
                    OutfitSlotPosition(0.50f, 0.04f, 0.46f, 0.66f),
                    OutfitSlotPosition(0.30f, 0.58f, 0.38f, 0.36f)
                )
                4 -> listOf(
                    OutfitSlotPosition(0.03f, 0.04f, 0.45f, 0.52f),
                    OutfitSlotPosition(0.52f, 0.04f, 0.45f, 0.52f),
                    OutfitSlotPosition(0.08f, 0.56f, 0.36f, 0.38f),
                    OutfitSlotPosition(0.56f, 0.56f, 0.36f, 0.38f)
                )
                else -> listOf(
                    OutfitSlotPosition(0.02f, 0.03f, 0.42f, 0.50f),
                    OutfitSlotPosition(0.50f, 0.02f, 0.48f, 0.60f),
                    OutfitSlotPosition(0.06f, 0.54f, 0.34f, 0.34f),
                    OutfitSlotPosition(0.42f, 0.62f, 0.24f, 0.25f),
                    OutfitSlotPosition(0.66f, 0.62f, 0.28f, 0.28f),
                    OutfitSlotPosition(0.26f, 0.78f, 0.20f, 0.17f)
                )
            }
            return slots[index.coerceAtMost(slots.lastIndex)]
        }
    }

    private data class OutfitSlotPosition(
        val leftRatio: Float,
        val topRatio: Float,
        val widthRatio: Float,
        val heightRatio: Float
    )

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()
}
