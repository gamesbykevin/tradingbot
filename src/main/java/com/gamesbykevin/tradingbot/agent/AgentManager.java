package com.gamesbykevin.tradingbot.agent;

import com.coinbase.exchange.api.entity.Product;
import com.gamesbykevin.tradingbot.calculator.Calculator;
import com.gamesbykevin.tradingbot.calculator.Calculator.Candle;
import com.gamesbykevin.tradingbot.util.LogFile;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import static com.gamesbykevin.tradingbot.agent.AgentHelper.HARD_STOP_RATIO;
import static com.gamesbykevin.tradingbot.agent.AgentManagerHelper.displayMessage;
import static com.gamesbykevin.tradingbot.agent.AgentManagerHelper.updateAgents;
import static com.gamesbykevin.tradingbot.calculator.Calculator.MY_TRADING_STRATEGIES;
import static com.gamesbykevin.tradingbot.util.LogFile.FILE_SEPARATOR;
import static com.gamesbykevin.tradingbot.util.LogFile.getFilenameManager;

public class AgentManager {

    //our agent list
    private List<Agent> agents;

    //our reference to the calculator
    private Calculator calculator;

    //are we updating the agent?
    private boolean working = false;

    //the product we are trading
    private final Product product;

    //current price of stock
    private double currentPrice = 0;

    //object used to write to a text file
    private PrintWriter writer;

    //how many funds did we start with
    private final double funds;

    /**
     * Different trading strategies we can use
     */
    public enum StrategyKey {
        AE,     BBAR,   BBER,   BBR,    CA,
        EMAR,   EMAS,   ERS,    FA,     FADOA,
        FAO,    FMFI,   HASO,   MACS,   MARS,
        MER,    MES,    RA,     RCR,    SOADX,
        SOEMA,  SSR
    }

    public AgentManager(final Product product, final double funds) {

        //store the product this agent is trading
        this.product = product;

        //how many funds do we start with?
        this.funds = funds;

        //create new calculator and perform our initial calculations
        this.calculator = new Calculator(getProductId(), getWriter());
        this.calculator.calculate(this, 0);

        //create our agents last
        createAgents();
    }

    private void createAgents() {

        //create our list of agents
        this.agents = new ArrayList<>();

        //create an agent for each strategy
        for (int i = 0; i < MY_TRADING_STRATEGIES.length; i++) {

            //create an agent for each hard stop ratio
            for (int j = 0; j < HARD_STOP_RATIO.length; j++) {

                //create our agent
                Agent agent = new Agent(getFunds(), getProductId(), MY_TRADING_STRATEGIES[i], MY_PERIOD_DURATIONS[k]);

                //assign the hard stop ratio
                agent.setHardStopRatio(HARD_STOP_RATIO[j]);

                //add agent to the list
                getAgents().add(agent);
            }
        }
    }

    public synchronized void update(final double price) {

        //if all agents have stopped trading don't continue
        if (hasStoppedTrading())
            return;

        //don't continue if we are currently working
        if (working)
            return;

        //flag that this agent is working
        working = true;

        //keep track of the current price
        setCurrentPrice(price);

        try {

            //update our calculator, etc...
            calculator.update(this);

            //update our agents
            updateAgents(this);

        } catch (Exception ex) {

            //print stack trace and write exception to log file
            ex.printStackTrace();
            displayMessage(ex, getWriter());

        } finally {

            //last step is to make that we are done working
            working = false;
        }
    }

    public String getAgentDetails() {

        String result = "\n";

        //sort the agents to show which are most profitable
        for (int i = 0; i < getAgents().size(); i++) {
            for (int j = i; j <  getAgents().size(); j++) {

                //don't check the same agent
                if (i == j)
                    continue;

                Agent agent1 = getAgents().get(i);
                Agent agent2 = getAgents().get(j);

                //if the next agent has more funds, switch
                if (getAssets(agent2) > getAssets(agent1)) {

                    //switch positions of our agents
                    getAgents().set(i, agent2);
                    getAgents().set(j, agent1);
                }
            }
        }

        //message with all agent totals
        for (int i = 0; i < getAgents().size(); i++) {

            Agent agent = getAgents().get(i);

            //start with product, strategy, hard stop ratio, and candle duration
            result += getProductId() + " : " + agent.getTradingStrategy() + ", " + agent.getDuration().description + ", " + agent.getHardStopRatio();

            //how much $ does the agent currently have
            result += " - $" + AgentHelper.round(getAssets(agent));

            //add our min value
            result += ",  Min $" + AgentHelper.round(agent.getFundsMin());

            //add our max value
            result += ",  Max $" + AgentHelper.round(agent.getFundsMax());

            //if this agent has stopped trading, include it in the message
            if (agent.hasStopTrading())
                result += ", (Stopped)";

            //make new line
            result += "\n";
        }

        //return our result
        return result;
    }

    public double getTotalAssets() {

        //total assets
        double result = 0;

        //add all of our assets up
        for (int i = 0; i < getAgents().size(); i++) {
            result += getTotalAssets(getAgents().get(i));
        }

        //return our result
        return result;
    }

    /**
     * Get the total assets
     * @return The total funds available + the quantity of stock we currently own @ the current stock price
     */
    public double getTotalAssets(Agent agent) {

        //return the total amount
        return getAssets(agent);
    }

    public double getTotalAssets(StrategyKey strategyKey, Candle duration, float ratio) {

        for (int i = 0; i < getAgents().size(); i++) {

            Agent agent = getAgents().get(i);

            if (agent.getDuration() == duration &&
                agent.getHardStopRatio() == ratio &&
                agent.getTradingStrategy() == strategyKey)
                return getAssets(agent);
        }

        return 0;
    }


    private double getAssets(Agent agent) {
        return agent.getAssets(getCurrentPrice());
    }

    public PrintWriter getWriter() {

        //create our object to write to a text file
        if (this.writer == null)
            this.writer = LogFile.getPrintWriter(getFilenameManager(), LogFile.getLogDirectory() + FILE_SEPARATOR + getProductId());

        //return our print writer
        return this.writer;
    }

    public double getFunds() {
        return (this.funds);
    }

    public void setCurrentPrice(final double currentPrice) {
        this.currentPrice = currentPrice;
    }

    public double getCurrentPrice() {
        return this.currentPrice;
    }

    public String getProductId() {
        return getProduct().getId();
    }

    public Product getProduct() {
        return this.product;
    }

    public List<Agent> getAgents() {
        return this.agents;
    }

    /**
     * Have all the agents stopped trading?
     * @return true = yes, false = no
     */
    public boolean hasStoppedTrading() {

        //check every agent
        for (int i = 0; i < getAgents().size(); i++) {

            //if one agent is still trading, return false
            if (!getAgents().get(i).hasStopTrading())
                return false;
        }

        //all agents are done return true
        return true;
    }
}