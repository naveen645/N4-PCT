import com.navis.argo.EdiRailCar
import com.navis.argo.EdiTrainVisit
import com.navis.argo.RailConsistTransactionDocument
import com.navis.argo.RailConsistTransactionsDocument
import com.navis.argo.RailRoad
import com.navis.external.edi.entity.AbstractEdiPostInterceptor
import com.navis.framework.persistence.HibernateApi
import com.navis.rail.business.entity.*
import com.navis.rail.business.entity.RailcarType
import org.apache.log4j.Level
import com.navis.rail.business.entity.Railroad
import org.apache.log4j.Logger
import org.apache.xmlbeans.XmlObject

class PNCTRailConsistEdiInterceptor extends AbstractEdiPostInterceptor {
    @Override
    void beforeEdiPost(XmlObject inXmlTransactionDocument, Map inParams) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("PNCTRailConsistEdiInterceptor started execution!!!!!!!")
        if (RailConsistTransactionsDocument.class.isAssignableFrom(inXmlTransactionDocument.getClass())) {
            RailConsistTransactionsDocument railConsistTransactionsDocument = (RailConsistTransactionsDocument) inXmlTransactionDocument
            RailConsistTransactionsDocument.RailConsistTransactions railConsistTransactions = railConsistTransactionsDocument.getRailConsistTransactions();
            LOGGER.info("railConsistTransactions::" + railConsistTransactions)
            List<RailConsistTransactionDocument.RailConsistTransaction> railConsistTransactionsList =
                    railConsistTransactions.getRailConsistTransactionList();
            if (railConsistTransactionsList != null && railConsistTransactionsList.size() == 1) {
                RailConsistTransactionDocument.RailConsistTransaction railConsistTransaction = railConsistTransactionsList.get(0);
                if (railConsistTransaction != null) {

                    RailConsistTransactionDocument.RailConsistTransaction.EdiRailCarVisit ediRailCarVisit = railConsistTransaction.getEdiRailCarVisit()
                    LOGGER.info("ediRailCarVisit::" + ediRailCarVisit)
                    EdiRailCar ediRailCar = ediRailCarVisit.getRailCar()
                    LOGGER.info("ediRailCar" + ediRailCar)
                    String railCarId = ediRailCar.getRailCarId();
                    LOGGER.info("railCarId" + railCarId)
                    String railCarType = ediRailCar.getRailCarType()
                    LOGGER.info("railCarType" + railCarType)

                    String roadId = ediRailCar.getRailCarOwner() != null ? ediRailCar.getRailCarOwner().getRailRoadId() : null
                    LOGGER.info("roadId" + roadId)
                    Railroad railRoad = Railroad.findRailroadById(roadId)
                    LOGGER.info("railRoad"+railRoad)
                    Railcar railcar
                     railcar = Railcar.findRailcar(railCarId)
                    LOGGER.info("railcar" + railcar)
                    RailcarType railcarType = RailcarType.findRailcarType(railCarType)
                    LOGGER.info("railcarType" + railcarType)
                    if (railcarType != null) {
                        if (railcar == null) {
                             railcar = Railcar.createRailcar(railCarId,railcarType,railRoad)
                            LOGGER.info("railcar::" + railcar)
                            HibernateApi.getInstance().save(railcar)

                        }

                    }

                }
            }
        }

    }
    private final static Logger LOGGER = Logger.getLogger(PNCTRailConsistEdiInterceptor.class)
}
