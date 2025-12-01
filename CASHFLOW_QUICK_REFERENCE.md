# Cash Flow Prediction - Quick Reference Card

## 📱 User Actions

| Action | Button/UI | Result |
|--------|-----------|--------|
| Analyze transactions | "💰 Predict Cash Flow" | Opens prediction dialog |
| View details | Tap summary card | Opens full prediction |
| Close prediction | "Close" button | Returns to main screen |

## 🎯 Key Metrics

| Metric | Description | Color Code |
|--------|-------------|------------|
| **Expected Income** | Predicted credits next month | Blue |
| **Expected Expenses** | Predicted debits next month | Red |
| **Net Cash Flow** | Income - Expenses | Green (surplus) / Red (deficit) |
| **Confidence** | Data quality indicator | Green (High) / Yellow (Medium) / Red (Low) |

## 📊 Confidence Levels

| Level | Transactions | Reliability |
|-------|--------------|-------------|
| **High** | 30+ | Very reliable - use for planning |
| **Medium** | 15-29 | Good guidance with some uncertainty |
| **Low** | <15 | Basic estimate - parse more SMS |

## 🔄 Recurring Transaction Detection

| Frequency | Interval | Example |
|-----------|----------|---------|
| Weekly | ≤7 days | Grocery shopping |
| Monthly | ≤35 days | Netflix, rent, utilities |
| Quarterly | ≤95 days | Insurance premiums |

## 📈 Trend Indicators

| Trend | Meaning | For Expenses | For Income |
|-------|---------|--------------|------------|
| **Increasing** | +20% from baseline | ⚠️ Warning | ✅ Good |
| **Stable** | Within ±20% | ✅ Good | ➡️ Neutral |
| **Decreasing** | -20% from baseline | ✅ Good | ⚠️ Warning |

## 💡 Insights Examples

| Insight Type | Example |
|--------------|---------|
| Balance | "💰 Expected surplus of ₹6,500.00 next month" |
| Savings | "📊 Current savings rate: 14.4%" |
| Top Category | "🛒 Highest spending: Food (32.5%)" |
| Subscriptions | "🔄 3 recurring subscriptions costing ₹1,200.00/month" |
| Trend Alert | "📈 Spending increasing in: Shopping, Transport" |

## 🗂️ Category Examples

| Category | Transaction Types |
|----------|-------------------|
| Food | Restaurants, groceries, food delivery |
| Transport | Uber, Ola, fuel, tolls |
| Bills | Electricity, water, phone, internet |
| Shopping | Amazon, Flipkart, retail stores |
| Health | Pharmacy, doctors, insurance |
| Subscription | Netflix, Spotify, gym, apps |
| Transfer | Bank transfers, NEFT, IMPS |
| Other | Uncategorized transactions |

## 🛠️ Developer Functions

```kotlin
// Trigger prediction
viewModel.predictCashFlow()

// Clear prediction
viewModel.clearCashFlowPrediction()

// Access result
val prediction: CashFlowPrediction? = viewModel.cashFlowPrediction.value

// Check status
val isProcessing: Boolean = viewModel.isPredicting.value
```

## 📁 File Structure

```
CashFlowPredictor.kt         ← Core algorithm
ChatViewModel.kt             ← State management
MainActivity.kt              ← UI components
  └─ CashFlowPredictionDialog  ← Full prediction view
  └─ Summary Card              ← Quick preview
```

## 🧮 Prediction Formula

```
Income Prediction:
  = Average of recent credit transactions

Expense Prediction:
  = (Recent Historical × 60%) + (Recurring Patterns × 40%)

Net Balance:
  = Predicted Income - Predicted Expenses

Confidence:
  = Based on transaction count (30+ = High)
```

## 🐛 Common Issues & Fixes

| Issue | Solution |
|-------|----------|
| No prediction button | Parse some SMS first |
| "Not enough data" | Parse 15+ messages |
| Low confidence | Parse 30+ messages for better accuracy |
| Wrong amounts | Edit parsed JSON for that SMS |
| Missing recurring | Need 3+ similar transactions |
| Inaccurate prediction | Verify parsed data is correct |

## ⚡ Performance

| Metric | Value |
|--------|-------|
| Analysis Time | <1 second (typical) |
| Memory Usage | ~100 KB for 100 SMS |
| Thread | Background (Dispatchers.Default) |
| UI Blocking | None (async with coroutines) |

## 🎨 UI Components

```kotlin
// Summary Card (always visible when prediction exists)
┌─────────────────────────────┐
│ 📊 Cash Flow Summary        │
│                             │
│ Next Month: ₹6,500 (High)  │
└─────────────────────────────┘

// Full Dialog (on tap)
┌─────────────────────────────┐
│ 💰 Cash Flow Prediction     │
│ Confidence: High            │
├─────────────────────────────┤
│ Summary: Income/Expenses    │
│ Insights: 5 items           │
│ Categories: Breakdown       │
│ Recurring: Subscriptions    │
└─────────────────────────────┘
```

## 📝 JSON Format (Parsed SMS)

```json
{
  "amount": 1250.00,
  "currency": "INR",
  "merchant": "AMAZON PAY",
  "type": "debit",
  "date": "2025-01-15",
  "account_tail": "1234",
  "balance": 5000.00,
  "raw_text": "Original SMS text"
}
```

## 🔐 Privacy

✅ **100% Local Processing**

- No data sent to servers
- No internet required
- SMS data stays on device
- AI runs locally

## 📱 Minimum Requirements

| Requirement | Value |
|-------------|-------|
| Transactions | 5+ (minimum) |
| Time Period | 7+ days recommended |
| SMS Types | Bank/payment messages |
| Permissions | READ_SMS granted |

## 🚀 Quick Start (3 Steps)

1. **Import**: Get SMS messages (last 30 days)
2. **Parse**: Extract data from 15+ messages
3. **Predict**: Click "💰 Predict Cash Flow"

## 📞 Support

| Resource | Location |
|----------|----------|
| User Guide | `HOW_TO_USE_CASH_FLOW.md` |
| Developer Guide | `DEVELOPER_GUIDE_CASHFLOW.md` |
| Feature Details | `CASH_FLOW_PREDICTION_FEATURE.md` |

---

**Version:** 1.0  
**Last Updated:** January 2025  
**Status:** ✅ Production Ready
