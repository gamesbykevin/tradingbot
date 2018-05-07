package com.gamesbykevin.tradingbot.calculator.strategy;

import com.gamesbykevin.tradingbot.agent.Agent;
import com.gamesbykevin.tradingbot.agent.AgentHelper;
import com.gamesbykevin.tradingbot.calculator.Calculation;
import com.gamesbykevin.tradingbot.calculator.Period;
import com.gamesbykevin.tradingbot.calculator.Period.Fields;

import java.util.List;

import static com.gamesbykevin.tradingbot.agent.AgentManagerHelper.displayMessage;
import static com.gamesbykevin.tradingbot.calculator.CalculatorHelper.getValue;

public abstract class Strategy extends Calculation {

    //does this strategy need to wait for new candle data to check for a buy signal?
    private boolean wait = false;

    protected Strategy() {
        //default constructor
    }

    public void adjustHardStopPrice(Agent agent, double currentPrice) {

        //figure out our increase and get half of that
        double increase = ((agent.getWallet().getPurchasePrice() * agent.getHardStopRatio()) * .5f);

        //adjust our hard stop price around the current price to protect our investment
        agent.adjustHardStopPrice(currentPrice + increase);
    }

    public abstract void checkBuySignal(Agent agent, List<Period> history, double currentPrice);

    public abstract void checkSellSignal(Agent agent, List<Period> history, double currentPrice);

    /**
     * Does the strategy need to wait?
     * @return true = we need to wait for new candle data, false = otherwise
     */
    public boolean hasWait() {
        return this.wait;
    }

    /**
     * Set the strategy to wait for new candle data
     * @param wait true = we want this strategy to wait for new candle data, false = otherwise
     */
    public void setWait(boolean wait) {
        this.wait = wait;
    }
}