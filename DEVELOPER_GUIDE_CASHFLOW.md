# Developer Guide: Cash Flow Prediction Feature

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                         MainActivity.kt                      │
│  - UI Layer (Composables)                                   │
│  - CashFlowPredictionDialog                                 │
│  - Summary Card with tap-to-expand                          │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────┐
│                       ChatViewModel.kt                       │
│  - State Management (StateFlow)                             │
│  - predictCashFlow() function                               │
│  - Coordinates data flow                                    │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────┐
│                    CashFlowPredictor.kt                      │
│  - Core Algorithm Logic                                     │
│  - Pattern Recognition                                       │
│  - Prediction Engine                                         │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────┐
│                    Data Sources                              │
│  - parsedJsonBySms: Map<String, String>                    │
│  - smsList: List<RawSms>                                    │
└─────────────────────────────────────────────────────────────┘
```

## Core Components

### 1. CashFlowPredictor.kt

#### Main Function

```kotlin
suspend fun predictCashFlow(
    parsedJsonMap: Map<String, String>,
    smsListMap: Map<String, RawSms>
): CashFlowPrediction
```

**Input:**

- `parsedJsonMap`: SMS ID → Parsed JSON string (from AI/heuristic)
- `smsListMap`: SMS ID → Raw SMS object (for timestamp fallback)

**Output:**

- `CashFlowPrediction` object with all analytics

#### Data Flow

```
Raw JSON Strings
     ↓
parseTransactions()  ← Validates & extracts transaction objects
     ↓
[List<ParsedTransaction>]
     ↓
     ├→ detectRecurringTransactions()  ← Pattern matching
     ├→ analyzeCategorySpending()      ← Aggregation & trends
     ├→ predictNextMonthIncome()       ← Forecasting
     ├→ predictNextMonthExpenses()     ← Forecasting
     └→ generateInsights()             ← Rule-based insights
     ↓
CashFlowPrediction
```

#### Key Algorithms

**Recurring Detection Algorithm:**

```kotlin
1. Group transactions by merchant name
2. For each merchant with 2+ transactions:
   a. Calculate intervals between transaction dates
   b. Compute average interval
   c. Classify frequency:
      - Weekly: ≤7 days
      - Monthly: ≤35 days
      - Quarterly: ≤95 days
   d. Calculate confidence based on interval consistency:
      - High variance → Low confidence
      - Low variance → High confidence
   e. Predict next occurrence date
```

**Category Trend Detection:**

```kotlin
1. Split transactions into first half & second half
2. Compare total spending:
   - Second > First * 1.2 → "Increasing"
   - Second < First * 0.8 → "Decreasing"
   - Otherwise → "Stable"
```

**Expense Prediction Formula:**

```kotlin
predictedExpense = (recentHistorical * 0.6) + (recurringPatterns * 0.4)

where:
- recentHistorical = avg of last 30 transactions
- recurringPatterns = sum of expected monthly/weekly recurring txns
```

### 2. ChatViewModel.kt

#### State Management

```kotlin
// Prediction result
private val _cashFlowPrediction = MutableStateFlow<CashFlowPrediction?>(null)
val cashFlowPrediction: StateFlow<CashFlowPrediction?> = _cashFlowPrediction

// Loading state
private val _isPredicting = MutableStateFlow(false)
val isPredicting: StateFlow<Boolean> = _isPredicting

// Predictor instance
private val cashFlowPredictor = CashFlowPredictor()
```

#### Function: predictCashFlow()

```kotlin
fun predictCashFlow() {
    viewModelScope.launch {
        _isPredicting.value = true
        _statusMessage.value = "Analyzing cash flow patterns..."
        
        try {
            val smsMap = _smsList.value.associateBy { it.id }
            val prediction = cashFlowPredictor.predictCashFlow(
                parsedJsonMap = _parsedJsonBySms.value,
                smsListMap = smsMap
            )
            _cashFlowPrediction.value = prediction
            _statusMessage.value = "Cash flow prediction complete!"
        } catch (e: Exception) {
            _statusMessage.value = "Prediction failed: ${e.message}"
        } finally {
            _isPredicting.value = false
        }
    }
}
```

**Thread Safety:**

- Uses `viewModelScope` for lifecycle-aware coroutines
- Predictor logic runs on `Dispatchers.Default` (CPU-intensive)
- State updates automatically propagate to UI via StateFlow

### 3. MainActivity.kt UI Components

#### CashFlowPredictionDialog

**Layout Structure:**

```
Dialog (85% height)
  └─ Surface (rounded, elevated)
      └─ LazyColumn (scrollable)
          ├─ Header (title + confidence)
          ├─ Summary Card (income/expenses/balance)
          ├─ Insights (list of cards)
          ├─ Category Breakdown (sorted by spending)
          └─ Recurring Transactions (top 10)
```

**Composable Signature:**

```kotlin
@Composable
fun CashFlowPredictionDialog(
    prediction: CashFlowPrediction,
    onDismiss: () -> Unit
)
```

**Color Coding:**

```kotlin
// Balance
color = if (predictedBalance >= 0) Primary else Error

// Confidence
color = when (confidence) {
    "High" → Primary
    "Medium" → Tertiary
    "Low" → Error
}

// Trends
color = when (trend) {
    "Increasing" → Error (warning)
    "Decreasing" → Primary (good for expenses)
    "Stable" → OnSurfaceVariant
}
```

## Data Models

### CashFlowPrediction

```kotlin
data class CashFlowPrediction(
    val nextMonthIncome: Double,        // Predicted income
    val nextMonthExpenses: Double,      // Predicted expenses
    val predictedBalance: Double,       // Income - Expenses
    val recurringTransactions: List<RecurringTransaction>,
    val categoryBreakdown: Map<String, CategorySpend>,
    val insights: List<String>,         // Human-readable insights
    val confidence: String              // "High", "Medium", "Low"
)
```

### RecurringTransaction

```kotlin
data class RecurringTransaction(
    val merchant: String,               // e.g., "Netflix"
    val averageAmount: Double,          // e.g., 649.0
    val frequency: String,              // "Monthly", "Weekly", etc.
    val category: String,               // e.g., "Subscription"
    val nextExpectedDate: String?,      // "2025-02-15"
    val confidence: Int                 // 0-100%
)
```

### CategorySpend

```kotlin
data class CategorySpend(
    val category: String,               // e.g., "Food"
    val totalSpent: Double,             // Total in category
    val transactionCount: Int,          // Number of txns
    val averagePerTransaction: Double,  // totalSpent / count
    val percentageOfTotal: Double,      // % of all expenses
    val trend: String                   // "Increasing", "Stable", "Decreasing"
)
```

## Testing Strategy

### Unit Tests (Recommended)

```kotlin
class CashFlowPredictorTest {
    
    @Test
    fun `empty data returns zero prediction with low confidence`() {
        // Given
        val predictor = CashFlowPredictor()
        val emptyMap = emptyMap<String, String>()
        
        // When
        val result = runBlocking {
            predictor.predictCashFlow(emptyMap, emptyMap())
        }
        
        // Then
        assertEquals(0.0, result.nextMonthIncome)
        assertEquals("Low", result.confidence)
    }
    
    @Test
    fun `detects monthly subscription correctly`() {
        // Given: 3 Netflix transactions, 30 days apart
        val parsedJson = mapOf(
            "1" to """{"amount":649,"merchant":"Netflix","type":"debit","date":"2024-11-15"}""",
            "2" to """{"amount":649,"merchant":"Netflix","type":"debit","date":"2024-12-15"}""",
            "3" to """{"amount":649,"merchant":"Netflix","type":"debit","date":"2025-01-15"}"""
        )
        
        // When
        val result = runBlocking {
            predictor.predictCashFlow(parsedJson, emptyMap())
        }
        
        // Then
        val recurring = result.recurringTransactions.find { it.merchant == "Netflix" }
        assertNotNull(recurring)
        assertEquals("Monthly", recurring?.frequency)
        assertTrue(recurring?.confidence ?: 0 > 80)
    }
}
```

### Integration Tests

```kotlin
class CashFlowViewModelTest {
    
    @Test
    fun `predictCashFlow updates state correctly`() = runTest {
        // Given
        val viewModel = ChatViewModel()
        // ... populate SMS and parsed data
        
        // When
        viewModel.predictCashFlow()
        advanceUntilIdle()
        
        // Then
        assertNotNull(viewModel.cashFlowPrediction.value)
        assertFalse(viewModel.isPredicting.value)
    }
}
```

### Manual Testing Checklist

- [ ] Empty data shows "Not enough data" message
- [ ] 5 transactions: Low confidence prediction
- [ ] 15 transactions: Medium confidence
- [ ] 30+ transactions: High confidence
- [ ] Recurring detection: 3+ identical merchants
- [ ] Category breakdown sums to 100%
- [ ] Income/expense separation correct
- [ ] Date parsing handles multiple formats
- [ ] UI scrolls smoothly with long data
- [ ] Dialog dismisses correctly
- [ ] Summary card clickable

## Performance Considerations

### Time Complexity

```
parseTransactions: O(n) where n = number of SMS
detectRecurring: O(m²) where m = number of unique merchants
  - Optimized by early filtering (m << n typically)
categoryAnalysis: O(n)
generateInsights: O(k) where k = number of categories

Overall: O(n + m²) ≈ O(n) in practice
```

### Memory Usage

```
Parsed Transactions: ~1 KB per transaction
Prediction Result: ~10-50 KB total
UI State: Minimal (StateFlow references)

For 100 SMS: ~100 KB memory footprint
```

### Optimization Tips

1. **Use Dispatchers.Default**: CPU-intensive calculations off main thread
2. **Lazy Evaluation**: Only compute when user requests
3. **Caching**: Store prediction result, don't recalculate on dialog reopen
4. **Timeout**: Already implemented (45s per SMS parse)

## Error Handling

### Graceful Degradation

```kotlin
try {
    val json = JSONObject(jsonString)
    // ... parse fields
} catch (e: Exception) {
    continue  // Skip malformed entry, don't crash
}
```

### Fallback Strategies

1. **Missing Date**: Use SMS timestamp
2. **Invalid Amount**: Skip transaction
3. **Unknown Merchant**: Use "Unknown" placeholder
4. **Parse Error**: Use heuristic parser fallback

### User-Facing Errors

```kotlin
catch (e: Exception) {
    _statusMessage.value = "Prediction failed: ${e.message}"
    _cashFlowPrediction.value = null
}
```

## Extending the Feature

### Adding New Insight Rules

```kotlin
// In generateInsights()
if (categoryBreakdown["Shopping"]?.trend == "Increasing") {
    insights.add("💳 Shopping expenses rising - review recent purchases")
}
```

### Custom Categories

```kotlin
// In TransactionRepo.kt or parsing logic
val customCategories = mapOf(
    "SWIGGY" to "Food Delivery",
    "UBER" to "Transport",
    "NETFLIX" to "Entertainment"
)
```

### Export Feature

```kotlin
fun exportPredictionToCSV(prediction: CashFlowPrediction): String {
    return buildString {
        appendLine("Category,Amount,Count,Trend")
        prediction.categoryBreakdown.forEach { (cat, spend) →
            appendLine("$cat,${spend.totalSpent},${spend.transactionCount},${spend.trend}")
        }
    }
}
```

### Notification Integration

```kotlin
// When deficit predicted
if (prediction.predictedBalance < 0) {
    notificationManager.showWarning(
        "Expected deficit of ₹${abs(prediction.predictedBalance)} next month"
    )
}
```

## Debugging Tips

### Enable Verbose Logging

```kotlin
// In CashFlowPredictor.kt
private val DEBUG = true

if (DEBUG) {
    Log.d("CashFlow", "Parsed ${transactions.size} valid transactions")
    Log.d("CashFlow", "Found ${recurring.size} recurring patterns")
}
```

### Inspect Intermediate Results

```kotlin
// In ViewModel
_statusMessage.value = "Found ${transactions.size} transactions, analyzing..."
```

### Test Data Generator

```kotlin
fun generateMockTransactions(): Map<String, String> {
    return mapOf(
        "1" to """{"amount":1000,"merchant":"Amazon","type":"debit","date":"2025-01-01"}""",
        "2" to """{"amount":50000,"merchant":"Salary","type":"credit","date":"2025-01-05"}""",
        // ... more test data
    )
}
```

## Dependencies

### Existing

- Kotlin Coroutines (for async)
- Jetpack Compose (for UI)
- StateFlow (for reactive state)
- org.json (for JSON parsing)

### No Additional Dependencies Required

All logic uses standard library and existing dependencies.

## Code Quality

### Naming Conventions

- Functions: `camelCase`, verb-first (`predictCashFlow`, `detectRecurring`)
- Data classes: `PascalCase`, noun-based (`CashFlowPrediction`)
- Private functions: prefixed with context (`internalParseSms`)

### Documentation

- KDoc comments on public functions
- Inline comments for complex algorithms
- README files for feature overview

### Best Practices Followed

✅ Separation of concerns (UI / ViewModel / Logic)
✅ Single Responsibility Principle
✅ Immutable data classes
✅ Null safety (Kotlin)
✅ Coroutine best practices
✅ Compose guidelines

---

**Last Updated:** January 2025
**Maintainer:** Development Team
**Status:** Production Ready
