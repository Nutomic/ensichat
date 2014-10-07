package com.nutomic.ensichat.fragments

import android.app.Fragment
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import com.nutomic.ensichat.R

class ContactsFragment extends Fragment {

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup,
        savedInstanceState: Bundle): View = {
    return inflater.inflate(R.layout.fragment_contacts, container, false)
  }

}
