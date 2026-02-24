    package com.example.micropad.ui

    import androidx.compose.foundation.layout.*
    import androidx.compose.material3.*
    import androidx.compose.runtime.*
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.unit.dp
    import com.example.micropad.data.model.DyeLabel
    import androidx.compose.foundation.background
    import androidx.compose.foundation.lazy.grid.GridCells
    import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
    import androidx.compose.foundation.lazy.grid.items
    import androidx.compose.ui.graphics.Color

    @Composable
    fun RoiLabelingScreen(
        viewModel: RoiViewModel
    ) {
        val rois by viewModel.rois.collectAsState()

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            items(rois) { roi ->

                var expanded by remember(roi.id) { mutableStateOf(false) }

                val b = roi.extractedColor.`val`[0].toFloat() / 255f
                val g = roi.extractedColor.`val`[1].toFloat() / 255f
                val r = roi.extractedColor.`val`[2].toFloat() / 255f

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    ) {

                        // Color preview
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(Color(r, g, b))
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "ROI ${roi.id}",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Box {

                            Button(
                                onClick = { expanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(roi.label?.displayName ?: "Select Label")
                            }

                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {

                                DyeLabel.values().forEach { label ->

                                    val alreadyUsed =
                                        rois.any { it.label == label && it.id != roi.id }

                                    DropdownMenuItem(
                                        text = { Text(label.displayName) },
                                        enabled = !alreadyUsed,
                                        onClick = {
                                            viewModel.assignLabel(roi.id, label)
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }}
