package com.example.projectgutenberg.ui

internal object ReaderPageLogic {
    private const val DECORATIVE_INITIAL_MAX_WIDTH_PX = 160
    private const val DECORATIVE_INITIAL_MAX_HEIGHT_PX = 220

    private val tagRegex = Regex("<[^>]+>")
    private val entityRegex = Regex("&[^;]+;")
    private val whitespaceRegex = Regex("\\s+")
    private val imgTagRegex = Regex("<img\\b[^>]*?/?>", RegexOption.IGNORE_CASE)
    private val initialBlockInnerRegex = Regex(
        """<(?:p|div)\b[^>]*>\s*((?:<span\b[^>]*>\s*)?<img\b[^>]*?/?>\s*(?:</span>\s*)?)</(?:p|div)>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val blockOpeningTagRegex = Regex("""<([a-z0-9]+)\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val sizeAttrRegex = Regex("""["'=:\s]([0-9]{1,4})""")
    private val letterImageNameRegex = Regex("""[/_ -][a-z](?:\.[a-z0-9]{2,5}|[_-])""", RegexOption.IGNORE_CASE)

    fun mergeDecorativeInitialBlocks(blocks: List<String>): List<String> {
        if (blocks.isEmpty()) return blocks

        val mergedBlocks = mutableListOf<String>()
        var index = 0
        while (index < blocks.size) {
            val currentBlock = blocks[index]
            val initialInnerMarkup = extractDecorativeInitialMarkup(currentBlock)
            if (initialInnerMarkup != null && index + 1 < blocks.size) {
                mergedBlocks += prependDecorativeInitialMarkup(initialInnerMarkup, blocks[index + 1])
                index += 2
                continue
            }

            mergedBlocks += currentBlock
            index += 1
        }
        return mergedBlocks
    }

    fun hasMeaningfulPageContent(page: String): Boolean {
        val visibleText = extractVisibleText(page)
        if (visibleText.isNotBlank()) return true

        val imageTags = imgTagRegex.findAll(page).map { it.value }.toList()
        if (imageTags.isEmpty()) return false
        return imageTags.any { !isDecorativeImageTag(it) }
    }

    fun isDecorativeImageTag(tag: String): Boolean {
        val normalized = tag.lowercase()
        val width = sizeAttrRegex.find(normalized.substringAfter("width", ""))?.groupValues?.getOrNull(1)?.toIntOrNull()
        val height = sizeAttrRegex.find(normalized.substringAfter("height", ""))?.groupValues?.getOrNull(1)?.toIntOrNull()
        return "align=\"left\"" in normalized ||
            "float:left" in normalized ||
            "class=\"drop" in normalized ||
            "class='drop" in normalized ||
            "class=\"initial" in normalized ||
            "class='initial" in normalized ||
            "src=\"drop" in normalized ||
            "src='drop" in normalized ||
            letterImageNameRegex.containsMatchIn(normalized) ||
            (width != null && width <= DECORATIVE_INITIAL_MAX_WIDTH_PX) ||
            (height != null && height <= DECORATIVE_INITIAL_MAX_HEIGHT_PX)
    }

    fun formatPageNumber(pageNumber: Int): String {
        if (pageNumber <= 0) return pageNumber.toString()
        if (pageNumber > 3999) return pageNumber.toString()

        val numerals = listOf(
            1000 to "M",
            900 to "CM",
            500 to "D",
            400 to "CD",
            100 to "C",
            90 to "XC",
            50 to "L",
            40 to "XL",
            10 to "X",
            9 to "IX",
            5 to "V",
            4 to "IV",
            1 to "I"
        )

        var remaining = pageNumber
        val builder = StringBuilder()
        numerals.forEach { (value, numeral) ->
            while (remaining >= value) {
                builder.append(numeral)
                remaining -= value
            }
        }
        return builder.toString()
    }

    private fun extractDecorativeInitialMarkup(block: String): String? {
        if (!isLikelyDecorativeInitialBlock(block)) return null
        return initialBlockInnerRegex.find(block)?.groupValues?.get(1)?.trim()
    }

    private fun prependDecorativeInitialMarkup(initialMarkup: String, block: String): String {
        val openingTag = blockOpeningTagRegex.find(block) ?: return "$initialMarkup$block"
        return block.replaceRange(openingTag.range, "${openingTag.value}$initialMarkup")
    }

    private fun isLikelyDecorativeInitialBlock(block: String): Boolean {
        if (extractVisibleText(block).isNotBlank()) return false
        val imageTags = imgTagRegex.findAll(block).map { it.value }.toList()
        if (imageTags.size != 1) return false
        return isDecorativeImageTag(imageTags.first())
    }

    private fun extractVisibleText(body: String): String {
        return tagRegex.replace(body, " ")
            .replace(entityRegex, " ")
            .replace(whitespaceRegex, " ")
            .trim()
    }
}
