import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.Hashtable;

public class BookSellerAgent extends Agent {
    // каталог книг агенту-продавця для продажу (звязвує назву книги з її ціною)
    private Hashtable<String, Integer> catalogue;
    // GUI яким "продавець" (юзер) може додавати книги до каталогу
    private BookSellerGui myGui;

    // Ініціалізація агенту
    protected void setup() {
        // Формальне створення каталогу
        catalogue = new Hashtable<String, Integer>();

        // Формальне створення та показ GUI
        myGui = new BookSellerGui(this);

        // Register the book-selling service in the yellow pages
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("book-selling");
        sd.setName("JADE-book-trading");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        // Додавання поведінки з агенту покупців (Add the behaviour serving queries from buyer agents)
        addBehaviour(new OfferRequestsServer());

        // Додавння поведінки заказів покупців (Add the behaviour serving purchase orders from buyer agents)
        addBehaviour(new PurchaseOrdersServer());
    }

    // Put agent clean-up operations here
    protected void takeDown() {
        // Deregister from the yellow pages
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        // Закриття GUI
        myGui.dispose();
        // Принт повідомлення про закінчення роботи агента
        System.out.println("Seller-agent "+getAID().getName()+" terminating.");
    }

    /**
     Принт повідомлення додавання книги з ціною якимось продавцем
     */
    public void updateCatalogue(final String title, final int price) {
        addBehaviour(new OneShotBehaviour() {
            public void action() {
                catalogue.put(title, price);
                System.out.println(title+" inserted into catalogue. Price = "+price);
            }
        });
    }

    /**
     Inner class OfferRequestsServer.
     Поведінка агентів-продавців до повідомлень пошуку книги агентами_покупцями
     Якщо є книга у каталозі, то продавець відповідає повідомленням-пропозицією з ціною.
     Інакше надситлається пловідомлення-відказ
     */
    private class OfferRequestsServer extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                // CFP повідомлення отримання, оброблення повідомлення
                String title = msg.getContent();
                ACLMessage reply = msg.createReply();

                Integer price = catalogue.get(title);
                if (price != null) {
                    // Книга є в каталозі, запропонувати ціну
                    reply.setPerformative(ACLMessage.PROPOSE);
                    reply.setContent(String.valueOf(price.intValue()));
                } else {
                    // Книги в каталозі немає
                    reply.setPerformative(ACLMessage.REFUSE);
                    reply.setContent("not-available");
                }
                myAgent.send(reply);
            } else {
                block();
            }
        }
    }

    private class PurchaseOrdersServer extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                // Повідомлення про прийняття пропозиції прийнято, обролення
                String title = msg.getContent();
                ACLMessage reply = msg.createReply();

                Integer price = catalogue.remove(title);
                if (price != null) {
                    reply.setPerformative(ACLMessage.INFORM);
                    System.out.println(title+" sold to agent "+msg.getSender().getName());
                } else {
                    reply.setPerformative(ACLMessage.FAILURE);
                    reply.setContent("not-available");
                }
                myAgent.send(reply);
            } else {
                block();
            }
        }
    }

}