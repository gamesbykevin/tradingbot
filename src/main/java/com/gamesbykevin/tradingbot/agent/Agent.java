package com.gamesbykevin.tradingbot.agent;

import com.coinbase.exchange.api.entity.Product;
import com.coinbase.exchange.api.orders.Order;
import com.gamesbykevin.tradingbot.Main;
import com.gamesbykevin.tradingbot.calculator.Calculator;
import com.gamesbykevin.tradingbot.transaction.Transaction;
import com.gamesbykevin.tradingbot.transaction.Transaction.Result;
import com.gamesbykevin.tradingbot.transaction.TransactionHelper;
import com.gamesbykevin.tradingbot.transaction.TransactionHelper.ReasonSell;
import com.gamesbykevin.tradingbot.util.Email;
import com.gamesbykevin.tradingbot.util.LogFile;
import com.gamesbykevin.tradingbot.wallet.Wallet;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import static com.gamesbykevin.tradingbot.agent.AgentManager.TradingStrategy;
import static com.gamesbykevin.tradingbot.agent.AgentHelper.*;
import static com.gamesbykevin.tradingbot.agent.AgentManager.displayMessage;
import static com.gamesbykevin.tradingbot.wallet.Wallet.STOP_TRADING_RATIO;

public class Agent {

    //list of wallet for each product we are investing
    private final Wallet wallet;

    //object used to write to a text file
    private final PrintWriter writer;

    //do we have an order?
    private Order order = null;

    //list of transactions
    private List<Transaction> transactions;

    //do we stop trading
    private boolean stopTrading = false;

    //number of attempts we try to verify the order
    private int attempts = 0;

    //the reason why we are selling
    private ReasonSell reason;

    //what is our trading strategy
    private final TradingStrategy strategy;

    //the product we are trading
    private final String productId;

    //what is our hard stop amount
    private double hardStop = 0;

    //let's keep track of how low and high our money goes
    private double fundsMin, fundsMax;

    //do we buy stock?
    private boolean buy = false;

    protected Agent(TradingStrategy strategy, double funds, String productId, String fileName) {

        //save the trading strategy we want to implement
        this.strategy = strategy;

        //create new list of transactions
        this.transactions = new ArrayList<>();

        //store the product reference
        this.productId = productId;

        //create our object to write to a text file
        this.writer = LogFile.getPrintWriter(fileName);

        //create a wallet so we can track our investments
        this.wallet = new Wallet(funds);

        //assign our min/max when we start
        this.setFundsMin(funds);
        this.setFundsMax(funds);

        //display message and write to file
        displayMessage(this, "Starting $" + funds, true);
    }

    public TradingStrategy getStrategy() {
        return this.strategy;
    }

    public String getProductId() {
        return this.productId;
    }

    protected synchronized void update(Calculator calculator, Product product, double currentPrice) {

        //skip if we lost too much $
        if (hasStopTrading())
            return;

        //get our current assets
        double assets = getAssets(currentPrice);

        //track our min/max value
        if (assets < getFundsMin()) {

            //set the new minimum if lower than previous
            setFundsMin(assets);

        } else if (assets > getFundsMax()) {

            //set new maximum if greater than previous
            setFundsMax(assets);

        }

        //if we don't have an active order look at the market data for a chance to buy
        if (getOrder() == null) {

            if (getWallet().getQuantity() > 0) {

                //we have quantity let's see if we can sell it
                checkSell(this, calculator, product, currentPrice);

            } else {

                //we don't have any quantity so let's see if we can buy
                checkBuy(this, calculator, product, currentPrice);

            }

            //reset our attempts counter, which is used when we create a limit order
            setAttempts(0);

        } else {

            //what is the status of our order
            AgentHelper.Status status = null;

            if (Main.PAPER_TRADING) {

                //if we are paper trading assume the order has been completed
                status = AgentHelper.Status.Filled;

            } else {

                //let's check if our order is complete
                status = updateLimitOrder(this, getOrder().getId());
            }

            //so what do we do now
            switch (status) {

                case Filled:

                    //update our wallet with the order info
                    fillOrder(getOrder(), product);

                    //now that the order has been filled, remove it
                    setOrder(null);
                    break;

                case Rejected:
                case Cancelled:

                    //if the order has been rejected or cancelled we will remove it
                    setOrder(null);
                    break;

                case Open:
                case Pending:
                case Done:

                    //do nothing
                    break;
            }

            //if we lost too much money and have no quantity pending, we will stop trading
            if (getWallet().getFunds() < (STOP_TRADING_RATIO * getWallet().getStartingFunds()) && getWallet().getQuantity() <= 0)
                setStopTrading(true);

            //if our money has gone up, increase the stop trading limit
            if (getWallet().getFunds() > getWallet().getStartingFunds()) {

                final double oldRatio = (STOP_TRADING_RATIO * getWallet().getStartingFunds());
                getWallet().setStartingFunds(getWallet().getFunds());
                final double newRatio = (STOP_TRADING_RATIO * getWallet().getStartingFunds());
                displayMessage(this, "Good news, stop trading limit has increased", true);
                displayMessage(this, "    Funds $" + AgentHelper.formatValue(getWallet().getFunds()), true);
                displayMessage(this, "Old limit $" + AgentHelper.formatValue(oldRatio), true);
                displayMessage(this, "New limit $" + AgentHelper.formatValue(newRatio), true);
                displayMessage(this, "If your funds fall below the new limit we will stop trading", true);
            }

            //notify if this agent has stopped trading
            if (hasStopTrading()) {

                String subject = "We stopped trading";
                String text1 = "Funds $" + AgentHelper.formatValue(getWallet().getFunds());
                String text2 = "Limit $" + AgentHelper.formatValue(STOP_TRADING_RATIO * getWallet().getStartingFunds());
                String text3 = "Min $" + AgentHelper.formatValue(getFundsMin());
                String text4 = "Max $" + AgentHelper.formatValue(getFundsMax());
                displayMessage(this, subject, true);
                displayMessage(this, text1, true);
                displayMessage(this, text2, true);
                displayMessage(this, text3, true);
                displayMessage(this, text4, true);

                //include the funds in our message
                String message = text1 + "\n";
                message += text2 + "\n";
                message += text3 + "\n";
                message += text4 + "\n";

                //also include the summary of wins/losses
                message = message + TransactionHelper.getDescWins(this) + "\n";
                message = message + TransactionHelper.getDescLost(this) + "\n";

                //send email notification
                Email.sendEmail(subject + " (" + getProductId() + "-" + getStrategy() + ")", message);
            }
        }
    }

    protected double getAssets(double currentPrice) {
        return (getWallet().getQuantity() * currentPrice) + getWallet().getFunds();
    }

    public PrintWriter getWriter() {
        return this.writer;
    }

    private void fillOrder(final Order order, final Product product) {

        if (order.getSide().equalsIgnoreCase(AgentHelper.Action.Buy.getDescription())) {

            //create a new transaction to track
            Transaction transaction = new Transaction();

            //update our transaction
            transaction.update(this, product, order);

            //add to our list
            this.transactions.add(transaction);

        } else if (order.getSide().equalsIgnoreCase(AgentHelper.Action.Sell.getDescription())) {

            //get the most recent transaction so we can complete it
            Transaction transaction = transactions.get(transactions.size() - 1);

            //update our transaction
            transaction.update(this, product, order);

            //display wins and losses
            displayMessage(this, TransactionHelper.getDescWins(this), true);
            displayMessage(this, TransactionHelper.getDescLost(this), true);

            //display average transaction time
            displayMessage(this, TransactionHelper.getAverageDurationDesc(this), true);

            //display the total $ amount invested in stocks
            displayMessage(this, AgentHelper.getStockInvestmentDesc(this), true);

            //display the count and reasons why we sold our stock
            TransactionHelper.displaySellReasonCount(this, Result.Win);
            TransactionHelper.displaySellReasonCount(this, Result.Lose);
        }
    }

    public void setOrder(final Order order) {
        this.order = order;
    }

    public Order getOrder() {
        return this.order;
    }

    public Wallet getWallet() {
        return this.wallet;
    }

    public void setStopTrading(boolean stopTrading) {
        this.stopTrading = stopTrading;
    }

    public boolean hasStopTrading() {
        return this.stopTrading;
    }

    protected void setAttempts(final int attempts) {
        this.attempts = attempts;
    }

    protected int getAttempts() {
        return this.attempts;
    }

    public void setReasonSell(final ReasonSell reason) {
        this.reason = reason;
    }

    public ReasonSell getReasonSell() {
        return this.reason;
    }

    public List<Transaction> getTransactions() {
        return this.transactions;
    }

    public double getHardStop() {
        return this.hardStop;
    }

    public void setHardStop(double hardStop) {
        this.hardStop = hardStop;
    }

    public double getFundsMax() {
        return this.fundsMax;
    }

    public double getFundsMin() {
        return this.fundsMin;
    }

    public void setFundsMax(double fundsMax) {
        this.fundsMax = fundsMax;
    }

    public void setFundsMin(double fundsMin) {
        this.fundsMin = fundsMin;
    }

    public void setBuy(boolean buy) {
        this.buy = buy;
    }

    public boolean hasBuy() {
        return this.buy;
    }
}