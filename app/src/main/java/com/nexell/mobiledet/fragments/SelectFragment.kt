// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.nexell.mobiledet.fragments

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import com.nexell.mobiledet.R
import com.nexell.mobiledet.databinding.FragmentSelectBinding


class SelectFragment : Fragment() {
    private lateinit var binding: FragmentSelectBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments.let {}
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentSelectBinding.inflate(layoutInflater)

        return binding.root
    }

    override fun onViewCreated(
        view: View, savedInstanceState: Bundle?
    ) {
        binding.imageButton.setOnClickListener {
            view.findNavController().navigate(R.id.action_selectFragment_to_imageFragment)
        }

    }
}
