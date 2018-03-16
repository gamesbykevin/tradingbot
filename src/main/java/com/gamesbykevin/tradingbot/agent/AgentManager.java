package com.gamesbykevin.tradingbot.agent;

import com.coinbase.exchange.api.entity.Product;
import com.gamesbykevin.tradingbot.calculator.Calculator;
import com.gamesbykevin.tradingbot.util.LogFile;
import com.gamesbykevin.tradingbot.util.PropertyUtil;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import static com.gamesbykevin.tradingbot.calculator.Calculator.HISTORICAL_PERIODS_MINIMUM;
import static com.gamesbykevin.tradingbot.calculator.Calculator.PERIODS_RSI;
import static com.gamesbykevin.tradingbot.util.Email.getFileDateDesc;

public class AgentManager {

    //list of agents we are trading with different strategies
    private final List<Agent> agents;

    //our reference to calculate calculator
    private final Calculator calculator;

    //when is the last time we loaded historical data
    private long previous;

    //are we updating the agent?
    private boolean working = false;

    //the duration of data we are checking
    private final Calculator.Duration myDuration;

    //the product we are trading
    private final Product product;

    //current price of stock
    private double currentPrice = 0;

    //object used to write to a text file
    private final PrintWriter writer;

    /**
     * Different trading strategies
     */
    public enum TradingStrategy {
        RSI, MACD, OBV, EMA,
        RSI_MACD
    }

    public AgentManager(final Product product, final double funds, final Calculator.Duration myDuration) {

        //store the product this agent is trading
        this.product = product;

        //create our object to write to a text file
        this.writer = LogFile.getPrintWriter(getFileName(null));

        //store our period duration
        this.myDuration = myDuration;

        //create our calculator
        this.calculator = new Calculator();

        //update the previous run time, so it runs immediately since we don't have data yet
        this.previous = System.currentTimeMillis() - (getMyDuration().duration * 1000);

        //create new list of agents
        this.agents = new ArrayList<>();

        //create an agent for every trading strategy
        for (TradingStrategy strategy : TradingStrategy.values()) {
            this.agents.add(new Agent(strategy, funds, product.getId(), getFileName(strategy)));
        }
    }

    public synchronized void update(final double price) {

        //don't continue if we are currently working
        if (working)
            return;

        //flag that this agent is working
        working = true;

        //keep track of the current price
        setCurrentPrice(price);

        try {

            //we don't need to update every second
            if (System.currentTimeMillis() - previous >= (getMyDuration().duration / 6) * 1000) {

                //display message as sometimes the call is not successful
                displayMessage("Making rest call to retrieve history " + getProductId(), false, getWriter());

                //get the size of the history
                final int size = getCalculator().getHistory().size();

                //update our historical data and update the last update
                boolean success = getCalculator().update(getMyDuration(), getProductId());

                //get the new size for comparison
                final int sizeNew = getCalculator().getHistory().size();

                if (success) {

                    //rest call is successful
                    displayMessage("Rest call successful. History size: " + sizeNew, (size != sizeNew), getWriter());

                } else {

                    //rest call isn't successful
                    displayMessage("Rest call is NOT successful.", true, getWriter());
                }

                this.previous = System.currentTimeMillis();
            }

            //make sure we have enough data before we start trading
            if (getCalculator().getHistory().size() < HISTORICAL_PERIODS_MINIMUM) {

                //we are not ready to trade yet
                displayMessage(getProductId() + ": Not enough periods to trade yet - " + getCalculator().getHistory().size() + " < " + HISTORICAL_PERIODS_MINIMUM, false, getWriter());

            } else {

                //update each agent trading this specific product with their own trading strategy
                for (Agent agent : getAgents()) {
                    agent.update(getCalculator(), getProduct(), getCurrentPrice());
                }
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            displayMessage(ex, true, getWriter());
        }

        //flag that we are no longer working
        working = false;
    }

    private List<Agent> getAgents() {
        return this.agents;
    }

    public void setCurrentPrice(final double currentPrice) {
        this.currentPrice = currentPrice;
    }

    public double getCurrentPrice() {
        return this.currentPrice;
    }

    public Calculator.Duration getMyDuration() {
        return this.myDuration;
    }

    public String getProductId() {
        return getProduct().getId();
    }

    protected Calculator getCalculator() {
        return this.calculator;
    }

    public Product getProduct() {
        return this.product;
    }

    /**
     * Get the total assets
     * @return The total funds available + the quantity of stock we currently own @ the current stock price
     */
    public double getAssets() {

        double amount = 0;

        for (Agent agent : agents) {

            //add the total amount
            amount += (agent.getWallet().getQuantity() * getCurrentPrice()) + agent.getWallet().getFunds();
        }

        //return the total amount
        return amount;
    }

    public static void displayMessage(Exception e, boolean write, PrintWriter writer) {
        PropertyUtil.displayMessage(e, write, writer);
    }

    public static void displayMessage(String message, boolean write, PrintWriter writer) {
        PropertyUtil.displayMessage(message, write, writer);
    }

    protected String getFileName(TradingStrategy strategy) {

        if (strategy == null) {
            return (getProductId() + "-" + getFileDateDesc() + ".log");
        } else {
            return (getProductId() + "-" + strategy.toString() + "-" + getFileDateDesc() + ".log");
        }
    }

    private PrintWriter getWriter() {
        return this.writer;
    }
}