package com.example.btl_nhung.domain.model

data class MapSnapshot(
    val rows: Int,
    val cols: Int,
    val robotRow: Int,
    val robotCol: Int,
    val cells: List<MapCellState>,
) {
    init {
        require(cells.size == rows * cols) { "cells.size must equal rows*cols" }
    }

    fun linearIndex(row: Int, col: Int): Int = row * cols + col
}
