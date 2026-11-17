package quest.saigon.shared.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import quest.saigon.shared.model.ProductDto
import quest.saigon.shared.model.ProductOrderDto
import quest.saigon.shared.network.ApiResult
import quest.saigon.shared.network.TownApiClient

data class ShopUiState(
    val query: String = "",
    val products: List<ProductDto> = emptyList(),
    val totalCount: Long = 0L,
    val nextCursor: String? = null,
    val selectedProduct: ProductDto? = null,
    val lastOrder: ProductOrderDto? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null,
)

class ShopStore(
    private val api: TownApiClient,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(ShopUiState())
    val state: StateFlow<ShopUiState> = _state.asStateFlow()

    fun loadInitial(query: String = "") {
        scope.launch {
            _state.update { it.copy(query = query, isLoading = true, isLoadingMore = false, error = null) }
            when (val res = api.searchProducts(query = query, cursor = null, limit = 50)) {
                is ApiResult.Success -> {
                    val page = res.value
                    _state.update {
                        it.copy(
                            products = page.products,
                            totalCount = page.count,
                            nextCursor = page.nextCursor,
                            isLoading = false,
                            error = null,
                        )
                    }
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(isLoading = false, error = res.message) }
                }
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        val cursor = current.nextCursor ?: return
        if (current.isLoading || current.isLoadingMore) return
        scope.launch {
            _state.update { it.copy(isLoadingMore = true, error = null) }
            when (val res = api.searchProducts(query = current.query, cursor = cursor, limit = 50)) {
                is ApiResult.Success -> {
                    val page = res.value
                    _state.update { prev ->
                        val seen = prev.products.mapTo(HashSet()) { it.id }
                        prev.copy(
                            products = prev.products + page.products.filter { seen.add(it.id) },
                            totalCount = page.count,
                            nextCursor = page.nextCursor,
                            isLoadingMore = false,
                        )
                    }
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(isLoadingMore = false, error = res.message) }
                }
            }
        }
    }

    fun openProduct(productId: String) {
        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val res = api.getProduct(productId)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(selectedProduct = res.value, isLoading = false) }
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(isLoading = false, error = res.message) }
                }
            }
        }
    }

    fun selectProduct(product: ProductDto?) {
        _state.update { it.copy(selectedProduct = product, lastOrder = null) }
    }

    suspend fun createProduct(
        name: String,
        price: String,
        category: String,
        description: String,
        photos: List<ByteArray>,
    ): ProductDto? {
        _state.update { it.copy(isSubmitting = true, error = null) }
        return when (val res = api.createProduct(name, price, category, description, photos)) {
            is ApiResult.Success -> {
                val created = res.value
                _state.update { prev ->
                    prev.copy(
                        products = listOf(created) + prev.products,
                        totalCount = prev.totalCount + 1,
                        selectedProduct = created,
                        isSubmitting = false,
                    )
                }
                created
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(isSubmitting = false, error = res.message) }
                null
            }
        }
    }

    suspend fun updateProduct(
        productId: String,
        name: String,
        price: String,
        category: String,
        description: String,
        photoOrderJson: String?,
        newPhotos: List<ByteArray>,
    ): ProductDto? {
        _state.update { it.copy(isSubmitting = true, error = null) }
        return when (val res = api.updateProduct(productId, name, price, category, description, photoOrderJson, newPhotos)) {
            is ApiResult.Success -> {
                val updated = res.value
                _state.update { prev ->
                    prev.copy(
                        products = prev.products.map { if (it.id == updated.id) updated else it },
                        selectedProduct = if (prev.selectedProduct?.id == updated.id) updated else prev.selectedProduct,
                        isSubmitting = false,
                    )
                }
                updated
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(isSubmitting = false, error = res.message) }
                null
            }
        }
    }

    suspend fun deleteProduct(productId: String): Boolean {
        _state.update { it.copy(isSubmitting = true, error = null) }
        return when (val res = api.deleteProduct(productId)) {
            is ApiResult.Success -> {
                _state.update { prev ->
                    prev.copy(
                        products = prev.products.filterNot { it.id == productId },
                        totalCount = (prev.totalCount - 1).coerceAtLeast(0L),
                        selectedProduct = if (prev.selectedProduct?.id == productId) null else prev.selectedProduct,
                        isSubmitting = false,
                    )
                }
                true
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(isSubmitting = false, error = res.message) }
                false
            }
        }
    }

    suspend fun placeOrder(productId: String, contactMethod: String, message: String): ProductOrderDto? {
        _state.update { it.copy(isSubmitting = true, error = null) }
        return when (val res = api.orderProduct(productId, contactMethod, message)) {
            is ApiResult.Success -> {
                _state.update { it.copy(lastOrder = res.value, isSubmitting = false) }
                res.value
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(isSubmitting = false, error = res.message) }
                null
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
