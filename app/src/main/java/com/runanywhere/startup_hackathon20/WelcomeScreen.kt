package com.runanywhere.startup_hackathon20

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WelcomeScreen(onComplete: () -> Unit) {
    var currentTip by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }

    val tips = listOf(
        WelcomeTip(
            Icons.Default.Lock,
            "Bank-Level Security",
            "Military-grade encryption • 100% on-device",
            Color(0xFF4CAF50)
        ),
        WelcomeTip(
            Icons.Default.Shield,
            "Your Data Never Leaves",
            "No cloud storage • No third-party access",
            Color(0xFF2196F3)
        ),
        WelcomeTip(
            Icons.Default.Security,
            "Scam Protection",
            "AI detects fraudulent transactions instantly",
            Color(0xFFEF5350)
        ),
        WelcomeTip(
            Icons.Default.PrivacyTip,
            "Complete Privacy",
            "No tracking • No data collection • No ads",
            Color(0xFFFF9800)
        )
    )

    LaunchedEffect(Unit) {
        delay(500)
        isLoading = false

        // Cycle through tips
        for (i in 0 until 4) {
            delay(1500)
            if (i < 3) currentTip = i + 1
        }
        delay(1000)
        onComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF6366F1),
                        Color(0xFF8B5CF6)
                    )
                )
            )
            .statusBarsPadding(),  // Add status bar padding to entire screen
        contentAlignment = Alignment.Center
    ) {
        // Skip button at top right - now properly visible below status bar
        Surface(
            onClick = onComplete,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = 16.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.White.copy(alpha = 0.2f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "Skip",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Skip",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            // Animated App Icon
            val scale by animateFloatAsState(
                targetValue = if (isLoading) 0.8f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "scale"
            )

            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(scale)
                    .background(Color.White.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalance,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Finance AI",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = "Your Personal Finance Coach",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.9f)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Animated Tips Carousel
            AnimatedContent(
                targetState = currentTip,
                transitionSpec = {
                    (slideInVertically { it } + fadeIn()).togetherWith(
                        slideOutVertically { -it } + fadeOut()
                    )
                },
                label = "tips"
            ) { index ->
                TipCard(tips[index])
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Progress Indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(4) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == currentTip) 24.dp else 8.dp, 8.dp)
                            .background(
                                if (index == currentTip) Color.White else Color.White.copy(alpha = 0.4f),
                                RoundedCornerShape(4.dp)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun TipCard(tip: WelcomeTip) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = tip.icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = tip.color
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = tip.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = tip.description,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )
        }
    }
}

data class WelcomeTip(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val color: Color
)
