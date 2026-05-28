package net.nealmcb.hourlyactivitytracker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Step count at or above this value triggers the highlight colour. */
private const val STEP_HIGHLIGHT_THRESHOLD = 250L

private val HighlightColor = Color(0xFF4CAF50)   // green
private val NormalColor    = Color(0xFF37474F)   // dark blue-grey
private val EmptyColor     = Color(0xFF263238)   // near-black

@Composable
fun HourlyActivityApp(
    uiState: UiState,
    onRequestPermissions: () -> Unit,
    onRefresh: () -> Unit
) {
    Scaffold(
        timeText = { TimeText() }
    ) {
        when (uiState) {
            is UiState.Loading -> LoadingScreen()
            is UiState.PermissionRequired -> PermissionScreen(onRequestPermissions)
            is UiState.HealthConnectUnavailable -> UnavailableScreen(reason = uiState.reason, onRetry = onRefresh)
            is UiState.Success -> HourlyGridScreen(
                date = uiState.date,
                hourlySteps = uiState.hourlySteps,
                onRefresh = onRefresh
            )
            is UiState.Error -> ErrorScreen(message = uiState.message, onRetry = onRefresh)
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun PermissionScreen(onRequestPermissions: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Steps permission needed",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.body2
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onRequestPermissions) {
            Text("Grant Access")
        }
    }
}

@Composable
private fun UnavailableScreen(reason: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = reason,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 12.dp),
            style = MaterialTheme.typography.body2
        )
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Error: $message",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.body2
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
private fun HourlyGridScreen(
    date: LocalDate,
    hourlySteps: List<HourlySteps>,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 24.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = date.format(DateTimeFormatter.ofPattern("MMM d")),
            style = MaterialTheme.typography.caption1,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = "Steps/hour  ≥250 = \uD83D\uDFE2",
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
            contentPadding = PaddingValues(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(hourlySteps) { entry ->
                HourCell(entry)
            }
        }
    }
}

@Composable
private fun HourCell(entry: HourlySteps) {
    val highlighted = entry.steps >= STEP_HIGHLIGHT_THRESHOLD
    val bgColor = when {
        highlighted    -> HighlightColor
        entry.steps > 0 -> NormalColor
        else           -> EmptyColor
    }

    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 36.dp)
            .background(color = bgColor, shape = RoundedCornerShape(4.dp))
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "%02d".format(entry.hour),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.75f),
                lineHeight = 10.sp
            )
            Text(
                text = if (entry.steps > 0) entry.steps.toString() else "–",
                fontSize = 9.sp,
                color = Color.White,
                lineHeight = 10.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
