package com.webscare.orangelinelahore.ui.onboarding

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.webscare.orangelinelahore.common.Utils.addPressEffect
import com.webscare.orangelinelahore.databinding.OnboardingScreenBinding

class OnboardingAdapter(
    private val images: List<Int>,
    private val titles: List<String>,
    private val descriptions: List<String>,
    private val onNextClickListener: (Int) -> Unit
) : RecyclerView.Adapter<OnboardingAdapter.OnboardingViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OnboardingViewHolder {
        val binding = OnboardingScreenBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return OnboardingViewHolder(binding, onNextClickListener)
    }

    override fun onBindViewHolder(holder: OnboardingViewHolder, position: Int) {
        holder.bind(images[position], titles[position], descriptions[position], position)
    }

    override fun getItemCount(): Int = images.size

    class OnboardingViewHolder(
        private val binding: OnboardingScreenBinding,
        private val onNextClickListener: (Int) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(image: Int, title: String, description: String, position: Int) {
            binding.onboardingImage.setImageResource(image)
            binding.onboardingTitle.text = title
            binding.onboardingDescription.text = description

            // Set the "Next" button text
            if (position == 2) {
                binding.next.text = "Let's Get Started"
            } else {
                binding.next.text = "Next"
            }

            binding.next.addPressEffect {
                onNextClickListener(position)
            }
        }
    }
}
