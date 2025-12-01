# Cash Flow Prediction Feature

## Overview

The Cash Flow Prediction feature analyzes transaction patterns from parsed SMS messages and predicts
future cash flows, helping users understand their financial health and plan better.

## Components Added

### 1. CashFlowPredictor.kt

**Location:** `app/src/main/java/com/runanywhere/startup_hackathon20/CashFlowPredictor.kt`

**Key Features:**

- **Smart Pattern Recognition**: Detects recurring transactions (subscriptions, bills)
- **Category Analysis**: Breaks down spending by category with trends
- **Next Month Prediction**: Forecasts income, expenses, and net balance
- **Confidence Scoring**: Rates prediction quality based on data availability
- **Actionable Insights**: Generates personalized financial insights

**Data Classes:**

- `CashFlowPrediction`: Main prediction result with all analytics
- `RecurringTransaction`: Detected recurring payment patterns
- `CategorySpend`: Category-wise spending analysis

**Algorithm Features:**

1. **Transaction Parsing**: Extracts valid transactions from parsed JSON
2. **Recurring Detection**: Groups by merchant, calculates frequency (Weekly/Monthly/Quarterly)
3. **Trend Analysis**: Compares spending patterns over time periods
4. **Prediction Logic**: Weights historical data (60%) + recurring patterns (40%)
5. **Insight Generation**: Automatic financial advice based on patterns

### 2. ChatViewModel.kt Updates

**Added:**

- Cash flow prediction state management
- `predictCashFlow()`: Triggers analysis
- `clearCashFlowPrediction()`: Resets prediction state
- State flows for prediction data and loading status

### 3. MainActivity.kt Updates

**Added:**

- **Predict Cash Flow Button**: Appears when SMS messages are parsed
- **CashFlowPredictionDialog**: Beautiful full-screen dialog showing:
    - Next month income/expense summary
    - Net cash flow prediction
    - Key financial insights
    - Spending breakdown by category with trends
    - Recurring transactions with confidence scores
    - Next expected payment dates

## How to Use

### Step 1: Import & Parse SMS

1. Grant SMS permissions
2. Import SMS messages (last 30 days)
3. Parse individual messages or use batch processing

### Step 2: Generate Prediction

1. Click the **"💰 Predict Cash Flow"** button
2. Wait for analysis (usually < 1 second)
3. View comprehensive prediction dialog

### Step 3: Understand Results

**Summary Card:**

- Expected Income next month
- Expected Expenses next month
- Net Cash Flow (positive = surplus, negative = deficit)

**Insights:**

- Savings rate
- Spending warnings
- Subscription costs
- Category trends

**Category Breakdown:**

- Total spent per category
- Percentage of total spending
- Trend (Increasing/Stable/Decreasing)

**Recurring Transactions:**

- Detected subscriptions and bills
- Average amount and frequency
- Next expected payment date
- Confidence score (0-100%)

## Technical Details

### Recurring Transaction Detection

```kotlin
- Groups transactions by merchant
- Calculates average interval between payments
- Classifies frequency: Weekly (≤7 days), Monthly (≤35 days), Quarterly (≤95 days)
- Confidence based on interval consistency
```

### Prediction Algorithm

```kotlin
Next Month Expenses = (Recent Historical * 0.6) + (Recurring Patterns * 0.4)
Next Month Income = Average of recent credits
Net Balance = Predicted Income - Predicted Expenses
```

### Confidence Levels

- **High**: 30+ transactions analyzed
- **Medium**: 15-29 transactions
- **Low**: <15 transactions

## UI/UX Features

### Visual Indicators

- **Green** text: Positive (surplus, savings, safe)
- **Red** text: Warning (deficit, increasing spend)
- **Tertiary** color: Informational

### Smart Display

- Categories sorted by spending amount
- Recurring transactions sorted by confidence
- Scrollable dialog for long content
- Responsive layout

### User-Friendly Format

- Currency formatted with 2 decimal places
- Percentages rounded to 1 decimal
- Dates in YYYY-MM-DD format
- Clear labels and grouping

## Example Output

```
💰 Cash Flow Prediction
Confidence: High

Next Month Summary
Expected Income: ₹45,000.00
Expected Expenses: ₹38,500.00
Net Cash Flow: ₹6,500.00

Key Insights:
💰 Expected surplus of ₹6,500.00 next month
📊 Current savings rate: 14.4%
🛒 Highest spending: Food (32.5%)
🔄 3 recurring subscriptions costing ₹1,200.00/month
📈 Spending increasing in: Shopping, Transport

Spending by Category:
Food: ₹12,450.00 (45 txns) - Stable
Transport: ₹5,200.00 (23 txns) - Increasing
Bills: ₹4,800.00 (8 txns) - Stable
...

Recurring Transactions:
Netflix - ₹649.00 • Monthly (Next: 2025-02-15) - 95% confident
Zomato Gold - ₹149.00 • Monthly (Next: 2025-02-10) - 90% confident
...
```

## Benefits

1. **Financial Awareness**: Users see where money goes
2. **Proactive Planning**: Predict shortfalls before they happen
3. **Subscription Management**: Identify forgotten subscriptions
4. **Spending Control**: Spot increasing expense categories
5. **Savings Goals**: Calculate actual savings rate

## Future Enhancements (Optional)

- Export predictions to CSV/PDF
- Set budget alerts for categories
- Compare month-over-month trends
- Seasonal spending patterns
- AI-powered personalized recommendations
- Goal tracking (savings targets)

## Testing

**Test Cases:**

1. No data: Shows "Not enough data" message
2. 5-10 transactions: Low confidence prediction
3. 15+ transactions: Medium confidence
4. 30+ transactions: High confidence with detailed insights
5. Mix of credit/debit: Accurate income vs expense separation
6. Recurring patterns: Correctly detects subscriptions

**Edge Cases Handled:**

- Missing dates: Uses SMS timestamp
- Malformed JSON: Skips invalid entries
- Single occurrence: Not marked as recurring
- Irregular patterns: Lower confidence score

---

**Implementation Date:** January 2025
**Version:** 1.0
**Status:** ✅ Complete and Ready for Use
