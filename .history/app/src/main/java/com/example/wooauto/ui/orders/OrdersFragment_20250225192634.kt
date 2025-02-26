package com.example.wooauto.ui.orders

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.Navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.wooauto.R
import com.example.wooauto.databinding.FragmentOrdersBinding
import com.google.android.material.chip.Chip
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class OrdersFragment : Fragment() {

    private var _binding: FragmentOrdersBinding? = null
    private val binding get() = _binding!!

    private val viewModel: OrderViewModel by viewModels()
    private lateinit var ordersAdapter: OrderAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOrdersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSearchView()
        setupStatusFilters()
        setupSwipeRefresh()

        observeViewModel()
    }

    private fun setupRecyclerView() {
        ordersAdapter = OrderAdapter(
            onOrderClick = { orderId ->
                navigateToOrderDetails(orderId)
            },
            onMarkAsCompleteClick = { orderId ->
                viewModel.markOrderAsComplete(orderId)
            }
        )

        binding.ordersRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = ordersAdapter
        }
    }

    private fun setupSearchView() {
        binding.searchEditText.setOnEditorActionListener { textView, _, _ ->
            viewModel.updateSearchQuery(textView.text.toString())
            true
        }
    }

    private fun setupStatusFilters() {
        binding.chipAll.setOnClickListener { viewModel.updateStatusFilter(null) }
        binding.chipPending.setOnClickListener { viewModel.updateStatusFilter("pending") }
        binding.chipProcessing.setOnClickListener { viewModel.updateStatusFilter("processing") }
        binding.chipCompleted.setOnClickListener { viewModel.updateStatusFilter("completed") }
        binding.chipCancelled.setOnClickListener { viewModel.updateStatusFilter("cancelled") }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.refreshOrders()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collectLatest { state ->
                        binding.progressBar.isVisible = state is OrdersUiState.Loading
                        binding.swipeRefreshLayout.isRefreshing = viewModel.isRefreshing.value

                        when (state) {
                            is OrdersUiState.Success -> {
                                binding.emptyView.isVisible = state.orders.isEmpty()
                                binding.ordersRecyclerView.isVisible = state.orders.isNotEmpty()
                                ordersAdapter.submitList(state.orders)
                            }
                            is OrdersUiState.Empty -> {
                                binding.emptyView.isVisible = true
                                binding.ordersRecyclerView.isVisible = false
                            }
                            is OrdersUiState.Error -> {
                                binding.emptyView.isVisible = true
                                binding.emptyView.text = state.message
                                binding.ordersRecyclerView.isVisible = false
                                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                            }
                            else -> { /* Loading state handled above */ }
                        }
                    }
                }

                launch {
                    viewModel.printAllState.collectLatest { state ->
                        when (state) {
                            is PrintAllState.Printing -> {
                                binding.printStatusTextView.isVisible = true
                                binding.printStatusTextView.text = "Printing all unprinted orders..."
                            }
                            is PrintAllState.Completed -> {
                                binding.printStatusTextView.isVisible = true
                                binding.printStatusTextView.text =
                                    "Printed ${state.printed} orders, ${state.failed} failed"
                            }
                            is PrintAllState.NoOrdersToPrint -> {
                                binding.printStatusTextView.isVisible = true
                                binding.printStatusTextView.text = "No unprinted orders to print"
                            }
                            is PrintAllState.Error -> {
                                binding.printStatusTextView.isVisible = true
                                binding.printStatusTextView.text = "Error: ${state.message}"
                            }
                            else -> binding.printStatusTextView.isVisible = false
                        }
                    }
                }
            }
        }
    }

    private fun navigateToOrderDetails(orderId: Long) {
        findNavController().navigate(
            OrdersFragmentDirections.actionOrdersFragmentToOrderDetailFragment(orderId)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}