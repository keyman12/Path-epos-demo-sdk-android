package tech.path2ai.epos.models

data class Product(
    val id: String,
    val name: String,
    val description: String,
    val price: Int,        // pence
    val symbolName: String, // Material icon name
    val category: String
)
