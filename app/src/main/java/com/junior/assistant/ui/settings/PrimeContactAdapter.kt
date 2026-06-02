package com.junior.assistant.ui.settings

import android.view.*
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.junior.assistant.R

data class PrimeContact(val name: String, val number: String)

class PrimeContactAdapter(
    private val contacts: MutableList<PrimeContact>,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<PrimeContactAdapter.VH>() {
    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name:   TextView    = v.findViewById(R.id.primeItemName)
        val number: TextView    = v.findViewById(R.id.primeItemNumber)
        val delete: ImageButton = v.findViewById(R.id.primeItemDelete)
    }
    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_prime_contact, p, false))
    override fun onBindViewHolder(h: VH, pos: Int) {
        h.name.text = contacts[pos].name; h.number.text = contacts[pos].number
        h.delete.setOnClickListener { onDelete(pos) }
    }
    override fun getItemCount() = contacts.size
}
