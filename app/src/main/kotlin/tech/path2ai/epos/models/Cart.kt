package tech.path2ai.epos.models

data class CartItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val product: Product,
    val quantity: Int
) {
    val lineTotal: Int get() = product.price * quantity
}
