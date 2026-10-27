// One file, four classes — all of them land in a single bundle "rahla.groovy.Toolbox".
//
// The package declaration is mandatory: a bundle can only export named packages, so classes in
// the default package would be invisible to the Blueprint context. Rahla rejects such a file with
// a clear message instead of installing something unusable.
package example

import org.apache.camel.Exchange
import org.apache.camel.Processor

/** Rejects orders without positions. Throwing hands the exchange to the route's error handler. */
class Validator implements Processor {

    void process(Exchange exchange) throws Exception {
        def order = exchange.message.body
        if (!order?.items) {
            throw new IllegalArgumentException("order ${order?.id} has no items")
        }
    }
}

/** Puts the computed values into headers, leaving the body alone. */
class Enricher implements Processor {

    void process(Exchange exchange) throws Exception {
        def order = exchange.message.body
        exchange.message.setHeader('orderId', order.id)
        exchange.message.setHeader('total', Orders.total(order))
        exchange.message.setHeader('enrichedBy', 'Toolbox v1')
    }
}

/** Replaces the body with a small summary that the REST binding marshals to JSON. */
class Summarizer implements Processor {

    void process(Exchange exchange) throws Exception {
        exchange.message.body = [
                orderId: exchange.message.getHeader('orderId'),
                total  : exchange.message.getHeader('total'),
                by     : exchange.message.getHeader('enrichedBy'),
        ]
    }
}

/** Plain helper, not a Processor — proves that ordinary classes work too. */
class Orders {

    /** Instance method so a route can call it via <bean ref="orders" method="sample"/>. */
    Map sample() {
        [id: 'A-1', items: [[price: 9.99, qty: 2], [price: 4.50, qty: 1]]]
    }

    static BigDecimal total(order) {
        order.items.sum { (it.price as BigDecimal) * (it.qty as int) } as BigDecimal
    }
}
