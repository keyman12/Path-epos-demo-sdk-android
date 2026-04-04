package tech.path2ai.epos.managers

import tech.path2ai.epos.models.Product

class InventoryManager {
    val products: List<Product>
    val categories: List<String>

    init {
        products = listOf(
            // Hot Drinks
            Product("1", "Espresso", "Single shot", 250, "local_cafe", "Hot Drinks"),
            Product("2", "Americano", "Long black coffee", 295, "local_cafe", "Hot Drinks"),
            Product("3", "Flat White", "Double shot, steamed milk", 340, "local_cafe", "Hot Drinks"),
            Product("4", "Cappuccino", "Espresso, steamed milk, foam", 340, "local_cafe", "Hot Drinks"),
            Product("5", "Latte", "Espresso with steamed milk", 350, "local_cafe", "Hot Drinks"),
            Product("6", "Hot Chocolate", "Rich cocoa, steamed milk", 375, "local_cafe", "Hot Drinks"),
            // Cold Drinks
            Product("7", "Iced Latte", "Espresso over ice with milk", 380, "ac_unit", "Cold Drinks"),
            Product("8", "Cold Brew", "Slow-steeped cold coffee", 350, "wine_bar", "Cold Drinks"),
            Product("9", "Orange Juice", "Freshly squeezed", 325, "water_drop", "Cold Drinks"),
            Product("10", "Sparkling Water", "Chilled sparkling", 195, "bubble_chart", "Cold Drinks"),
            // Food
            Product("11", "Croissant", "Butter croissant, baked fresh", 275, "bakery_dining", "Food"),
            Product("12", "Toast & Jam", "Sourdough, strawberry jam", 295, "restaurant", "Food"),
            Product("13", "Avocado Toast", "Sourdough, smashed avocado", 595, "dining", "Food"),
            Product("14", "Granola Bowl", "Greek yogurt, granola, honey", 495, "spa", "Food"),
            // Snacks
            Product("15", "Brownie", "Double chocolate", 325, "favorite", "Snacks"),
            Product("16", "Flapjack", "Golden syrup oat bar", 275, "star", "Snacks"),
            Product("17", "Banana", "Single banana", 80, "eco", "Snacks"),
            Product("18", "Crisps", "Sea salt, 40g", 125, "shopping_bag", "Snacks"),
            Product("19", "Energy Bar", "Peanut butter & oat", 225, "cookie", "Snacks")
        )
        categories = listOf("All") + products.map { it.category }.distinct()
    }

    fun productsIn(category: String): List<Product> =
        if (category == "All") products else products.filter { it.category == category }
}
