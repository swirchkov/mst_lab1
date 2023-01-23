import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.Arrays;
import java.util.Vector;

public class BookBuyerAgent extends Agent {
    // Назва книги для покупки
    private String targetBookTitle;
    // Список агентів-продавців, у яких є книга
    private AID[] sellerAgents;

    // Ініціалізація агента
    protected void setup() {
        // Принт повідомлення про початок роботи агента-покупця
        System.out.println("Hallo! Buyer-agent "+getAID().getName()+" is ready.");

        // Начальним аргументом роботи агнента-покупця є книга для покупки
        Object[] args = {"1984"};
        if (args != null && args.length > 0) {
            targetBookTitle = (String) args[0];
            System.out.println("Target book is "+targetBookTitle);

            // Додавання поведінки типу TickerBehaviour, яка подає пропозицію на пошук книги агентам-продавцям кожну хвилину
            addBehaviour(new TickerBehaviour(this, 10000) {
                protected void onTick() {
                    System.out.println("Trying to buy "+targetBookTitle);
                    // Оновити список агентів-продавців, у яких є книга
                    DFAgentDescription template = new DFAgentDescription();
                    ServiceDescription sd = new ServiceDescription();
                    sd.setType("book-selling");
                    template.addServices(sd);
                    try {
                        DFAgentDescription[] result = DFService.search(myAgent, template);
                        System.out.println("Found the following seller agents:");
                        sellerAgents = new AID[result.length];
                        for (int i = 0; i < result.length; ++i) {
                            sellerAgents[i] = result[i].getName();
                            System.out.println(sellerAgents[i].getName());
                        }
                    } catch (FIPAException fe) {
                        fe.printStackTrace();
                    }

                    // Відправити повідомлення про пошук
                    myAgent.addBehaviour(new RequestPerformer());
                }
            } );
        } else {
            // Заставити агента термінуватися
            System.out.println("No target book title specified");
            doDelete();
        }
    }

    // Put agent clean-up operations here
    protected void takeDown() {
        // Повідомлення про термінування агента-покупця
        System.out.println("Buyer-agent "+getAID().getName()+" terminating.");
    }

    /**
     Inner class RequestPerformer.
     Поведінка для агентів-покупців для запросу у агентів-продавців книги пошуку
     */
    private class RequestPerformer extends Behaviour {
        private AID bestSeller; // агент з найкращою пропозицією
        private int bestPrice;  // найкраща ціна
        private int repliesCnt = 0; // лічильник відповідей від агентів-продавців
        private MessageTemplate mt; // зразок отримання відповідей
        private int step = 0;

        public void action() {
            switch (step) {
                case 0:
                    // Послати cfp до всіх агентів-продавців
                    ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                    for (int i = 0; i < sellerAgents.length; ++i) {
                        cfp.addReceiver(sellerAgents[i]);
                    }
                    cfp.setContent(targetBookTitle);
                    cfp.setConversationId("book-trade");
                    cfp.setReplyWith("cfp"+System.currentTimeMillis()); // Unique value
                    myAgent.send(cfp);
                    // Підговувати зразок для отримання пропозицій
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("book-trade"),
                            MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
                    step = 1;
                    break;
                case 1:
                    // Отримати всі пропозиції/відкази від агентів-продавців
                    ACLMessage reply = myAgent.receive(mt);
                    if (reply != null) {
                        // Отримання відповіді
                        if (reply.getPerformative() == ACLMessage.PROPOSE) {
                            // Вибір пропозиції
                            int price = Integer.parseInt(reply.getContent());
                            if (bestSeller == null || price < bestPrice) {
                                // Вибір найкращої на даний час пропозиції
                                bestPrice = price;
                                bestSeller = reply.getSender();
                            }
                        }
                        repliesCnt++;
                        if (repliesCnt >= sellerAgents.length) {
                            // Отримали всі відповіді
                            step = 2;
                        }
                    } else {
                        block();
                    }
                    break;
                case 2:
                    // Відправити ордер на купівлю у агента-продавця з найкращою пропозицією
                    ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                    order.addReceiver(bestSeller);
                    order.setContent(targetBookTitle);
                    order.setConversationId("book-trade");
                    order.setReplyWith("order"+System.currentTimeMillis());
                    myAgent.send(order);
                    // Підготувати зразок для отримання відповіді на ордер купівлі
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("book-trade"),
                            MessageTemplate.MatchInReplyTo(order.getReplyWith()));
                    step = 3;
                    break;
                case 3:
                    // Отримати відповідь на купівлю книги
                    reply = myAgent.receive(mt);
                    if (reply != null) {
                        // Purchase order reply received
                        if (reply.getPerformative() == ACLMessage.INFORM) {
                            // Купівля прошла успішно, агент термінується
                            System.out.println(targetBookTitle+" successfully purchased from agent "+reply.getSender().getName());
                            System.out.println("Price = "+bestPrice);
                            myAgent.doDelete();
                        } else {
                            System.out.println("Attempt failed: requested book already sold.");
                        }

                        step = 4;
                    } else {
                        block();
                    }
                    break;
            }
        }

        public boolean done() {
            if (step == 2 && bestSeller == null) {
                System.out.println("Attempt failed: "+targetBookTitle+" not available for sale");
            }
            return ((step == 2 && bestSeller == null) || step == 4);
        }
    }
}