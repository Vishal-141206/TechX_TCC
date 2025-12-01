# How to Use Cash Flow Prediction

## Quick Start Guide

### Step-by-Step Instructions

#### 1. **Launch the App**

Open your app and you'll see the main chat interface with model management.

#### 2. **Download and Load a Model** (One-time setup)

- Click **"Models"** button in the top bar
- Download the Qwen model (374 MB)
- Once downloaded, click **"Load"**
- Wait for "Model loaded!" message

#### 3. **Grant SMS Permissions**

- Click **"Grant Permissions"** button
- Allow SMS and Audio permissions when prompted

#### 4. **Import Your SMS Messages**

- Click **"Import SMS"** button
- The app will scan your last 30 days of messages
- You'll see a preview of imported messages (up to 6 shown)

#### 5. **Parse Transaction Messages**

For each SMS you can:

- Click **"Parse"** to extract transaction details (amount, merchant, type, etc.)
- Click **"Scam"** to check if it's a suspicious message
- Click **"Edit"** to manually correct parsed JSON

**Tip:** Parse at least 5-10 messages for better predictions!

#### 6. **Generate Cash Flow Prediction** ⭐

- Once you have parsed some messages, you'll see a **"💰 Predict Cash Flow"** button
- Click it to analyze your financial patterns
- A beautiful prediction dialog will appear showing:
    - Next month's expected income and expenses
    - Your predicted balance (surplus or deficit)
    - Key insights about your spending
    - Category breakdown with trends
    - Recurring transactions (subscriptions, bills)

#### 7. **Review Your Prediction**

The prediction dialog shows:

**Top Section:**

```
Next Month Summary
Expected Income: ₹45,000.00
Expected Expenses: ₹38,500.00
Net Cash Flow: ₹6,500.00  ← Green = surplus, Red = deficit
```

**Key Insights:**

- Automatic financial advice
- Savings rate calculation
- Top spending categories
- Subscription costs
- Spending trend warnings

**Category Breakdown:**

- See where your money goes
- Identify increasing expenses
- Track transaction counts

**Recurring Transactions:**

- Spot subscriptions you might have forgotten
- See next payment dates
- Confidence scores for each detection

#### 8. **Take Action**

Based on insights:

- Cancel unused subscriptions
- Reduce spending in increasing categories
- Plan for expected deficit
- Set savings goals

## Example Scenario

### Day 1: Setup

```
1. Download Qwen model (374 MB)
2. Load the model
3. Grant SMS permissions
4. Import SMS → Found 45 financial messages
```

### Day 2: Parse Messages

```
1. Click "Parse" on 10-15 bank SMS
2. Review extracted amounts, merchants, categories
3. Edit any incorrect data if needed
```

### Day 3: Predict

```
1. Click "💰 Predict Cash Flow"
2. See prediction: ₹5,200 surplus expected next month
3. Review insights:
   - Savings rate: 12.8%
   - 3 subscriptions costing ₹1,200/month
   - Food spending increased 15%
4. Take action:
   - Cancel 1 unused subscription (Netflix duplicate account)
   - Set budget alert for food category
```

## Tips for Best Results

### 1. **Parse More Messages**

- Minimum: 5 messages (Low confidence)
- Good: 15-20 messages (Medium confidence)
- Best: 30+ messages (High confidence)

### 2. **Include Both Income & Expenses**

- Parse salary credits
- Parse bill debits
- Parse shopping expenses
- Parse transfers

### 3. **Parse Recent Messages**

- Focus on last 30 days
- More recent = more accurate predictions
- Older patterns may not reflect current spending

### 4. **Verify Parsed Data**

- Check if amounts are correct
- Verify merchant names
- Confirm transaction types (debit/credit)
- Use "Edit" to fix errors

### 5. **Regular Updates**

- Re-import SMS every week
- Parse new messages
- Re-run prediction to track changes
- Monitor spending trends

## Understanding Confidence Levels

**High Confidence (30+ transactions)**

- Very accurate predictions
- Detailed recurring transaction detection
- Reliable trend analysis
- Trust the numbers for planning

**Medium Confidence (15-29 transactions)**

- Good predictions with some uncertainty
- May miss some recurring patterns
- Use as rough guidance
- Parse more messages for better accuracy

**Low Confidence (<15 transactions)**

- Basic predictions only
- Limited pattern detection
- Use with caution
- Need more data for reliable forecasts

## Troubleshooting

### "No messages to process"

- Grant SMS permissions first
- Click "Import SMS"
- Check if you have bank/payment SMS in last 30 days

### "Not enough transaction data"

- Parse more messages (aim for 15+)
- Include both income and expense messages
- Verify parsed JSON is valid (not "Parsing..." or blank)

### Low confidence predictions

- Parse more messages (target 30+)
- Include variety of transaction types
- Wait to accumulate more SMS over time

### Prediction seems inaccurate

- Check parsed data for errors
- Use "Edit" to fix incorrect amounts
- Ensure transaction types are correct (debit vs credit)
- Re-run prediction after corrections

## Privacy & Security

✅ **All Processing is Local**

- SMS data never leaves your device
- AI model runs on your phone
- No internet connection required for analysis
- Your financial data is 100% private

✅ **Permissions Used**

- READ_SMS: To analyze transaction messages
- RECORD_AUDIO: For future voice features
- No location, contacts, or other invasive permissions

## Next Steps

After mastering cash flow prediction:

1. Set monthly spending budgets
2. Track savings goals
3. Export predictions (future feature)
4. Share insights with family (without sharing raw SMS)
5. Plan major purchases based on surplus predictions

---

**Need Help?**

- Review the prediction insights carefully
- Check CASH_FLOW_PREDICTION_FEATURE.md for technical details
- Parse more messages if predictions seem off
- Verify your parsed transaction data is accurate

**Enjoy smarter financial planning! 💰📊**
