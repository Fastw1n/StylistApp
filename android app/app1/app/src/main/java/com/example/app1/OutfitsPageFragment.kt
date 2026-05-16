package com.example.app1

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment

class OutfitsPageFragment : Fragment(R.layout.fragment_outfits_page) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.createOutfitCard).setOnClickListener {
            openFragment(OutfitCreatorFragment(), "create_outfit")
        }

        view.findViewById<View>(R.id.recommendedOutfitsCard).setOnClickListener {
            openFragment(RecommendedOutfitsFragment(), "recommended_outfits")
        }

        view.findViewById<View>(R.id.myOutfitsCard).setOnClickListener {
            openFragment(UserOutfitsFragment(), "user_outfits")
        }

        view.findViewById<View>(R.id.addClothingCard).setOnClickListener {
            openFragment(CameraPageFragment(), "add_clothing_from_outfits")
        }
    }

    private fun openFragment(fragment: Fragment, backStackName: String) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.framelayout, fragment)
            .addToBackStack(backStackName)
            .commit()
    }
}
