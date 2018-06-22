package pivotgraph;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.semanticweb.owl.align.Alignment;
import org.semanticweb.owl.align.AlignmentException;
import org.semanticweb.owl.align.Cell;

import fr.inrialpes.exmo.align.parser.AlignmentParser;
import sameAs.Link;
import sameAs.LinkList;

public class PivotGraph {

	public static void main(String[] args) throws AlignmentException, IOException, URISyntaxException {

		long startTime = System.currentTimeMillis();

		System.out.println("Just a heads up : I am running");
		Model d1Model = ModelFactory.createDefaultModel();
		Model d2Model = ModelFactory.createDefaultModel();
		Model d3Model = ModelFactory.createDefaultModel();

		Model pgModel = ModelFactory.createDefaultModel();			//we're gonna put the single links and triangles there
		//Model pgModel = ModelFactory.createDefaultModel();			//same for two-links
		//Model pgModel = ModelFactory.createDefaultModel();	//same for conflicts 
		Model plusModel = ModelFactory.createDefaultModel();		//same for multiple links
		
		// bnf = regular 1,1G bnf
		// bnf_short = first 180M of bnf, quite useless
		// bnf_f22Only = only F22 type resources of bnf
		InputStream d1In = new FileInputStream(new File("store/pp.ttl"));
		InputStream d2In = new FileInputStream(new File("store/bnf_f22only.ttl"));
		InputStream d3In = new FileInputStream(new File("store/rf.ttl"));

		StatBundle[] stats = new StatBundle[9];// total - 0,2-0,3 - 0,31-0,4 - [...] - 0,91-0,1,61-0,8 -> 0,81-1
		for (int i = 0; i < 9; i++) {
			stats[i] = new StatBundle();
		}

		int surelinks = 0;		//triangles
		int linkstovalid = 0;	//conflicts
		int createlinks = 0;	//single links
		int inferredlinks = 0;	//two-links but similarity of both is 1
		int plus = 0;			//multiple links from one uri to multiple uris in the same base
		int twolinks = 0;		//two-links
		int wasted = 0;			//single links "wasted" by multiple links 
								//i.e. a->b, b->c,d,e      a->b is "wasted"
		int noEqPP = 0;
		int noEqBNF = 0;
		int noEqRF = 0;
		
		ArrayList<String> compromised = new ArrayList<>();
		//this list is to prevent already read resources to be processed again
		//useful when dealing with some situations involving a lot of multiple links
		
		/***************
		 * Models loading
		 ***************/
		System.out.println("Models loading ...");
		d1Model.read(d1In, null, "TTL"); // pp
		System.out.println("PP loaded");
		d2Model.read(d2In, null, "TTL"); // bnf
		System.out.println("BNF loaded");
		d3Model.read(d3In, null, "TTL"); // rf
		System.out.println("RF loaded");
		long modelTime = System.currentTimeMillis();
		/***************
		 * Links loading
		 ***************/
		System.out.println("Links loading ...");
		AlignmentParser aparser = new AlignmentParser(0);
		Alignment results = aparser.parse(new File("store/finalResults.rdf").toURI());
		List<Cell> list = Collections.list(results.getElements());
		LinkList linkList = new LinkList();
		for (Cell cell : list) {
			Link link = new Link(cell.getObject1().toString(), cell.getObject2().toString(), cell.getStrength());
			linkList.add(link);
		}
		System.out.println("Total links loaded : "+linkList.size());
		long linkTime = System.currentTimeMillis();
		/**************************
		 * Pivot graph construction
		 **************************/

		System.out.println("Pivot graph construction ...");
		StmtIterator iter1 = d2Model.listStatements();
		boolean notequal = true;
		Resource prevres = null;
			while (iter1.hasNext()) {
				Statement stmt = iter1.nextStatement();
			// bnf_rf : équivalences de bnf dans rf
			// donc, contient des ressources de rf

			List<String> bnf_rf = new ArrayList<String>(); // Level 1 = Les objects de la 1ere ressource
			Resource res = stmt.getSubject();

			// dirty fix to avoid testing the same resource multiple times
			// to test the old method, simply replace that false by true
			if (prevres != null) {
				if (prevres.equals(res)) {
					notequal = false;
				} else
					notequal = true;
			}

			if (notequal) {
				if (hasType(res) == true) // si la ressource est de type F22
				{
					String subj = res.toString(); // Pour chaque ressource Rb de la BNF
					bnf_rf = linkList.getObj2OfURI1(subj); // Extraire ses equivalences de RF

					/************
					 * Safe Links
					 ************/
					
					if (bnf_rf.size() == 1) // Si RB a une seule ressource equivalente RF
					{
						
						//this is to handle a very special case : when a resource has multiple links on both sides
						
						if(!compromised.contains(subj)) {
							List<String> rf_bnf = new ArrayList<>();
							rf_bnf = linkList.getObj1OfURI2(bnf_rf.get(0));
							if(rf_bnf.size()>1) {
								for(String s : rf_bnf) {
									compromised.add(s);
									plus++;
									addStat(stats, "plu", linkList.getSimScore(s, bnf_rf.get(0)));
								}
								compromised.add(bnf_rf.get(0));
								for(String s : linkList.getObj2OfURI1(bnf_rf.get(0))) {
									compromised.add(s);
									plus++;
									addStat(stats, "plu", linkList.getSimScore(bnf_rf.get(0), s));
									for(String s2 : linkList.getObj2OfURI1(s)) {
										compromised.add(s2);
										wasted++;
										addStat(stats, "was", linkList.getSimScore(s, s2));
									}
								}
								for(String s : linkList.getObj1OfURI2(subj)){
									compromised.add(s);
									wasted++;
									addStat(stats, "was", linkList.getSimScore(s, subj));
								}
							}
						}
						
						if(!compromised.contains(subj)) {
						
							List<String> rf_pp = new ArrayList<String>();
							rf_pp = linkList.getObj2OfURI1(bnf_rf.get(0)); // Extraire les equivalences a RF de RP
							if (rf_pp.size() == 1) // Si RF a une seule ressource equivalente RP
							{
								List<String> pp_bnf = new ArrayList<String>();
								pp_bnf = linkList.getObj2OfURI1(rf_pp.get(0)); // Extraire les equivalences a RP de Rb
								if (pp_bnf.size() == 1) // Si RP a une seule ressource equivalente RB
								{
									if (pp_bnf.get(0).equals(subj)) // Case 1 = sure links
									{
										// System.out.println("sure links");
										surelinks = surelinks + 1;
										String identifier1 = getID(d2Model, subj);
										String identifier2 = getID(d3Model, bnf_rf.get(0));
										String identifier3 = getID(d1Model, rf_pp.get(0));
										Resource rsrce = pgModel.createResource((ConstructURI.build("bnf", identifier1, "rf", identifier2, "pp", identifier3, "F22_SelfContainedExpression")).toString());
										rsrce.addProperty(OWL.sameAs, pgModel.createResource(subj));
										rsrce.addProperty(OWL.sameAs, pgModel.createResource(bnf_rf.get(0)));
										rsrce.addProperty(OWL.sameAs, pgModel.createResource(rf_pp.get(0)));
										addStat(stats, "tri", linkList.getSimScore(subj, bnf_rf.get(0)));
										addStat(stats, "tri", linkList.getSimScore(bnf_rf.get(0), rf_pp.get(0)));
										addStat(stats, "tri", linkList.getSimScore(rf_pp.get(0), subj));
									} else // Case 3 = conflit
									{
										linkstovalid = linkstovalid + 1;
										String identifier1 = getID(d2Model, subj);
										String identifier2 = getID(d3Model, bnf_rf.get(0));
										String identifier3 = getID(d1Model, rf_pp.get(0));
										Resource rsrce = pgModel.createResource((ConstructURI.build("bnf", identifier1, "rf", identifier2, "pp", identifier3, "F22_SelfContainedExpression")).toString());
										rsrce.addProperty(OWL.sameAs, pgModel.createResource(subj));
										rsrce.addProperty(OWL.sameAs, pgModel.createResource(bnf_rf.get(0)));
										rsrce.addProperty(OWL.sameAs, pgModel.createResource(rf_pp.get(0)));
										rsrce.addProperty(OWL.sameAs, pgModel.createResource(pp_bnf.get(0)));
										addStat(stats, "con", linkList.getSimScore(subj, bnf_rf.get(0)));
										addStat(stats, "con", linkList.getSimScore(bnf_rf.get(0), rf_pp.get(0)));
										addStat(stats, "con", linkList.getSimScore(rf_pp.get(0), pp_bnf.get(0)));
									}
								} else if (pp_bnf.size() == 0)  // <RB = RF> & <RF = RP> mais nous ne trouvons pas d'equivalence de RP avec RB
								{
									double sim1 = linkList.getSimScore(subj, bnf_rf.get(0));
									double sim2 = linkList.getSimScore(bnf_rf.get(0), rf_pp.get(0));
									if (sim1 == 1 & sim2 == 1) // Si les 2 liens ont une similarite=1 alors on infere le
																// 3eme lien et on cree un URI pivot
									{
										Link link = new Link(rf_pp.get(0), subj, 1);	//created the inferred link
										linkList.add(link);
										inferredlinks = inferredlinks + 1;
										surelinks = surelinks + 1;
										String identifier1 = getID(d2Model, subj);
										String identifier2 = getID(d3Model, bnf_rf.get(0));
										String identifier3 = getID(d1Model, rf_pp.get(0));
										Resource rsrce = pgModel.createResource((ConstructURI.build("bnf", identifier1, "rf", identifier2,"pp", identifier3, "F22_SelfContainedExpression")).toString());
										rsrce.addProperty(OWL.sameAs, pgModel.createResource(subj));
										rsrce.addProperty(OWL.sameAs, pgModel.createResource(bnf_rf.get(0)));
										rsrce.addProperty(OWL.sameAs, pgModel.createResource(rf_pp.get(0)));
										addStat(stats, "inf", linkList.getSimScore(subj, bnf_rf.get(0)));
										addStat(stats, "inf", linkList.getSimScore(bnf_rf.get(0), rf_pp.get(0)));
									} else {	//sim < 1
										twolinks++;
										String identifier1 = getID(d2Model, subj);
										String identifier2 = getID(d3Model, bnf_rf.get(0));
										String identifier3 = getID(d1Model, rf_pp.get(0));
										Resource rsrce = pgModel.createResource((ConstructURI.build("bnf", identifier1, "rf", identifier2,"pp", identifier3, "F22_SelfContainedExpression")).toString());
										rsrce.addProperty(OWL.sameAs, pgModel.createResource(subj));
										rsrce.addProperty(OWL.sameAs, pgModel.createResource(bnf_rf.get(0)));
										rsrce.addProperty(OWL.sameAs, pgModel.createResource(rf_pp.get(0)));
										addStat(stats, "two", linkList.getSimScore(subj, bnf_rf.get(0)));
										addStat(stats, "two", linkList.getSimScore(bnf_rf.get(0), rf_pp.get(0)));
									}
								} else { //plusieurs
									plus = plus + pp_bnf.size();
									wasted = wasted+2;
									String identifier1 = getID(d2Model, subj);
									String identifier2 = getID(d3Model, bnf_rf.get(0));
									String identifier3 = getID(d1Model, rf_pp.get(0));
									Resource rsrce = plusModel.createResource((ConstructURI.build("bnf", identifier1, "rf", identifier2,"pp", identifier3, "F22_SelfContainedExpression")).toString());
									rsrce.addProperty(OWL.sameAs, plusModel.createResource(subj));
									rsrce.addProperty(OWL.sameAs, plusModel.createResource(bnf_rf.get(0)));
									rsrce.addProperty(OWL.sameAs, plusModel.createResource(rf_pp.get(0)));
									addStat(stats, "was", linkList.getSimScore(subj, bnf_rf.get(0)));
									addStat(stats, "was", linkList.getSimScore(bnf_rf.get(0), rf_pp.get(0)));
									for(int i=0; i<pp_bnf.size(); i++) {
										rsrce.addProperty(OWL.sameAs, plusModel.createResource(pp_bnf.get(i)));
										addStat(stats, "plu", linkList.getSimScore(rf_pp.get(0), pp_bnf.get(i)));
									}
								}
							} else if (rf_pp.size() == 0) // Case 2
							// si pas d'equivalence entre RF et RP
							{
								List<String> bnf_pp = new ArrayList<String>();
								bnf_pp = linkList.getObj1OfURI2(subj); 
								if(bnf_pp.size()==1) { 
									double sim1 = linkList.getSimScore(subj, bnf_rf.get(0));
									double sim2 = linkList.getSimScore(bnf_pp.get(0), subj);
									if (sim1 == 1 & sim2 == 1) // Si les 2 liens ont une similarite=1 alors on infere le
																// 3eme lien et on cree un URI pivot
									{
										Link link = new Link(rf_pp.get(0), subj, 1);	//created the inferred link
										linkList.add(link);
										inferredlinks = inferredlinks + 1;
										surelinks = surelinks + 1;
										String identifier1 = getID(d2Model, subj);
										String identifier2 = getID(d3Model, bnf_rf.get(0));
										String identifier3 = getID(d1Model, bnf_pp.get(0));
										Resource rsrce = pgModel.createResource((ConstructURI.build("bnf", identifier1, "rf", identifier2,"pp", identifier3, "F22_SelfContainedExpression")).toString());
										rsrce.addProperty(OWL.sameAs, pgModel.createResource(subj));
										rsrce.addProperty(OWL.sameAs, pgModel.createResource(bnf_rf.get(0)));
										rsrce.addProperty(OWL.sameAs, pgModel.createResource(bnf_pp.get(0)));
										addStat(stats, "inf", linkList.getSimScore(subj, bnf_rf.get(0)));
										addStat(stats, "inf", linkList.getSimScore(bnf_pp.get(0), subj));
									} else {	//sim < 1
										twolinks++;
										String identifier1 = getID(d2Model, subj);
										String identifier2 = getID(d3Model, bnf_rf.get(0));
										String identifier3 = getID(d1Model, bnf_pp.get(0));
										Resource rsrce = pgModel.createResource((ConstructURI.build("bnf", identifier1, "rf", identifier2,"pp", identifier3, "F22_SelfContainedExpression")).toString());
										rsrce.addProperty(OWL.sameAs, pgModel.createResource(subj));
										rsrce.addProperty(OWL.sameAs, pgModel.createResource(bnf_rf.get(0)));
										rsrce.addProperty(OWL.sameAs, pgModel.createResource(bnf_pp.get(0)));
										addStat(stats, "two", linkList.getSimScore(subj, bnf_rf.get(0)));
										addStat(stats, "two", linkList.getSimScore(bnf_pp.get(0), subj));
									}
								} else {
									createlinks = createlinks + 1;
									String identifier1 = getID(d2Model, subj);
									String identifier2 = getID(d3Model, bnf_rf.get(0));
									Resource rsrce = pgModel.createResource((ConstructURI.build("bnf", identifier1, "rf",
										identifier2, "F22_SelfContainedExpression")).toString());
									rsrce.addProperty(OWL.sameAs, pgModel.createResource(subj));
									rsrce.addProperty(OWL.sameAs, pgModel.createResource(bnf_rf.get(0)));
									addStat(stats, "one", linkList.getSimScore(subj, bnf_rf.get(0)));
								}
							} else {	//plusieurs
								plus = plus + rf_pp.size();
								wasted++;
								String identifier1 = getID(d2Model, subj);
								String identifier2 = getID(d3Model, bnf_rf.get(0));
								String identifier3 = getID(d1Model, rf_pp.get(0));
								Resource rsrce = plusModel.createResource((ConstructURI.build("bnf", identifier1, "rf", identifier2,"pp", identifier3, "F22_SelfContainedExpression")).toString());
								rsrce.addProperty(OWL.sameAs, plusModel.createResource(subj));
								rsrce.addProperty(OWL.sameAs, plusModel.createResource(bnf_rf.get(0)));
								addStat(stats, "was", linkList.getSimScore(subj, bnf_rf.get(0)));
								for(int i=0; i<rf_pp.size(); i++) {
									rsrce.addProperty(OWL.sameAs, plusModel.createResource(rf_pp.get(i)));
									addStat(stats, "plu", linkList.getSimScore(bnf_rf.get(0), rf_pp.get(i)));
								}
							}
						}
					} else if (bnf_rf.size() > 1 && !compromised.contains(subj)) {
						// System.out.println("plusieurs");
						plus = plus + bnf_rf.size();
						String identifier1 = getID(d2Model, subj);
						String identifier2 = getID(d3Model, bnf_rf.get(0));
						Resource rsrce = plusModel.createResource((ConstructURI.build("bnf", identifier1, "rf", identifier2, "F22_SelfContainedExpression")).toString());
						rsrce.addProperty(OWL.sameAs, plusModel.createResource(subj));
						for(int i=0; i<bnf_rf.size(); i++) {
							rsrce.addProperty(OWL.sameAs, plusModel.createResource(bnf_rf.get(i)));
							addStat(stats, "plu", linkList.getSimScore(subj, bnf_rf.get(i)));
						}
						
						
					} else if(!compromised.contains(subj)){ // si pas de lien entre bnf et rf
						List<String> bnf_pp = new ArrayList<String>();
						bnf_pp = linkList.getObj1OfURI2(subj); // équivalences de bnf dans pp
						if (bnf_pp.size() == 1) {
							List<String> pp_rf = new ArrayList<String>();
							pp_rf = linkList.getObj1OfURI2(bnf_pp.get(0)); // équivalences de pp dans rf

	
							if (pp_rf.size() == 1) { // lien à inférer
								double sim1 = linkList.getSimScore(subj, bnf_pp.get(0));
								double sim2 = linkList.getSimScore(bnf_pp.get(0), pp_rf.get(0));
								if (sim1 == 1 & sim2 == 1) // Si les 2 liens ont une similarite=1 alors on infere le 3eme lien et on cree un URI pivot
								{
									Link link = new Link(pp_rf.get(0), subj, 1);
									linkList.add(link);
									inferredlinks = inferredlinks + 1;
									surelinks++;
									String identifier1 = getID(d2Model, subj);
									String identifier2 = getID(d1Model, bnf_pp.get(0));
									String identifier3 = getID(d3Model, pp_rf.get(0));
									Resource rsrce = pgModel.createResource((ConstructURI.build("bnf", identifier1, "pp", identifier2,"rf", identifier3, "F22_SelfContainedExpression")).toString());
									rsrce.addProperty(OWL.sameAs, pgModel.createResource(subj));
									rsrce.addProperty(OWL.sameAs, pgModel.createResource(bnf_pp.get(0)));
									rsrce.addProperty(OWL.sameAs, pgModel.createResource(pp_rf.get(0)));
									addStat(stats, "inf", linkList.getSimScore(subj, bnf_pp.get(0)));
									addStat(stats, "inf", linkList.getSimScore(bnf_pp.get(0), pp_rf.get(0)));
								} else { //sim <1
									twolinks++;
									String identifier1 = getID(d2Model, subj);
									String identifier2 = getID(d1Model, bnf_pp.get(0));
									String identifier3 = getID(d3Model, pp_rf.get(0));
									Resource rsrce = pgModel.createResource((ConstructURI.build("bnf", identifier1, "pp", identifier2,"rf", identifier3, "F22_SelfContainedExpression")).toString());
									rsrce.addProperty(OWL.sameAs, pgModel.createResource(subj));
									rsrce.addProperty(OWL.sameAs, pgModel.createResource(bnf_pp.get(0)));
									rsrce.addProperty(OWL.sameAs, pgModel.createResource(pp_rf.get(0)));
									addStat(stats, "two", linkList.getSimScore(subj, bnf_pp.get(0)));
									addStat(stats, "two", linkList.getSimScore(bnf_pp.get(0), pp_rf.get(0)));
								}
							} else if (pp_rf.size() > 1) { //plusieurs
								plus = plus + pp_rf.size();
								wasted++;
								String identifier1 = getID(d2Model, subj);
								String identifier2 = getID(d3Model, bnf_pp.get(0));
								String identifier3 = getID(d1Model, pp_rf.get(0));
								Resource rsrce = plusModel.createResource((ConstructURI.build("bnf", identifier1, "pp", identifier2,"rf", identifier3, "F22_SelfContainedExpression")).toString());
								rsrce.addProperty(OWL.sameAs, plusModel.createResource(subj));
								rsrce.addProperty(OWL.sameAs, plusModel.createResource(bnf_pp.get(0)));
								addStat(stats, "was", linkList.getSimScore(subj, bnf_pp.get(0)));
								for(int i=0; i<pp_rf.size(); i++) {
									rsrce.addProperty(OWL.sameAs, plusModel.createResource(pp_rf.get(i)));
									addStat(stats, "plu", linkList.getSimScore(bnf_pp.get(0), pp_rf.get(0)));
								}
								
							} else if (pp_rf.size() == 0) {
								createlinks = createlinks + 1;
								String identifier1 = getID(d2Model, subj);
								String identifier2 = getID(d1Model, bnf_pp.get(0));
								Resource rsrce = pgModel.createResource((ConstructURI.build("bnf", identifier1, "pp",
										identifier2, "F22_SelfContainedExpression")).toString());
								rsrce.addProperty(OWL.sameAs, pgModel.createResource(subj));
								rsrce.addProperty(OWL.sameAs, pgModel.createResource(bnf_pp.get(0)));
								addStat(stats, "one", linkList.getSimScore(subj, bnf_pp.get(0)));
							}
						} else if (bnf_pp.size()>1) { //plusieurs
							plus = plus + bnf_pp.size();
							String identifier1 = getID(d2Model, subj);
							String identifier2 = getID(d3Model, bnf_pp.get(0));
							Resource rsrce = plusModel.createResource((ConstructURI.build("bnf", identifier1, "pp", identifier2, "F22_SelfContainedExpression")).toString());
							rsrce.addProperty(OWL.sameAs, plusModel.createResource(subj));
							for(int i=0; i<bnf_pp.size(); i++) {
								rsrce.addProperty(OWL.sameAs, plusModel.createResource(bnf_pp.get(i)));
								addStat(stats, "plu", linkList.getSimScore(subj, bnf_pp.get(i)));
							}
						}
					}
				}
			}
			prevres = stmt.getSubject();
		}
		long graphTime = System.currentTimeMillis();

		System.out.println("Vérification des liens uniques entre PP et RF");
		// détection des liens uniques entre pp et rf
		StmtIterator iter2 = d1Model.listStatements();
		notequal = true;
		prevres = null;
		while (iter2.hasNext()) {
			Statement stmt = iter2.nextStatement();

			List<String> pp_bnf = new ArrayList<String>(); // liens de pp à bnf (doivent être vides)
			Resource res = stmt.getSubject();

			if (prevres != null) {
				if (prevres.equals(res)) {
					notequal = false;
				} else
					notequal = true;
			}

			if (notequal) {
				if (hasType(res) == true) {
					String subj = res.toString(); // chaque ressource de PP
					pp_bnf = linkList.getObj2OfURI1(subj); // //liens de pp à bnf(doivent êtres vides)
					List<String> pp_rf = new ArrayList<String>(); // liens de pp à rf (doit être à un)
					pp_rf = linkList.getObj1OfURI2(subj);
					if (pp_bnf.size() == 0 && pp_rf.size() == 1) {
						List<String> rf_bnf = new ArrayList<String>(); // doit aussi être vide
						rf_bnf = linkList.getObj1OfURI2(pp_rf.get(0));
						if (rf_bnf.size() == 0) {
							createlinks = createlinks + 1;
							String identifier1 = getID(d1Model, subj);
							String identifier2 = getID(d3Model, pp_rf.get(0));
							Resource rsrce = pgModel.createResource((ConstructURI.build("pp", identifier1, "rf",
									identifier2, "F22_SelfContainedExpression")).toString());
							rsrce.addProperty(OWL.sameAs, pgModel.createResource(subj));
							rsrce.addProperty(OWL.sameAs, pgModel.createResource(pp_rf.get(0)));
							addStat(stats, "one", linkList.getSimScore(subj, pp_rf.get(0)));
						}
					}
				}
			}
			prevres = stmt.getSubject();
		}
		long bonusGraphTime = System.currentTimeMillis();

		// Creer un URI pivot pour chaque ressource qui n'a pas du tout d'equivalence
		List<String> classnames = new ArrayList<String>();
		classnames.add("http://erlangen-crm.org/efrbroo/F22_Self-Contained_Expression");

		List<Resource> ppRsrces = getResources(d1Model, classnames);
		for (Resource res : ppRsrces) {
			String resPP = res.toString();
			if (linkList.hasEquivalence(resPP) == false) {
				noEqPP = noEqPP + 1;
				String identifier1 = getID(d1Model, resPP);
				Resource rsrce = pgModel.createResource(
						(ConstructURI.build("pp", identifier1, "F22_SelfContainedExpression")).toString());
				rsrce.addProperty(OWL.sameAs, pgModel.createResource(resPP));
			}
		}

		List<Resource> bnfRsrces = getResources(d2Model, classnames);
		for (Resource res : bnfRsrces) {
			String resBNF = res.toString();
			if (linkList.hasEquivalence(resBNF) == false) {
				noEqBNF = noEqBNF + 1;
				String identifier2 = getID(d2Model, resBNF);
				Resource rsrce = pgModel.createResource(
						(ConstructURI.build("bnf", identifier2, "F22_SelfContainedExpression")).toString());
				rsrce.addProperty(OWL.sameAs, pgModel.createResource(resBNF));
			}
		}

		List<Resource> rfRsrces = getResources(d3Model, classnames);
		for (Resource res : rfRsrces) {
			String resRF = res.toString();
			if (linkList.hasEquivalence(resRF) == false) {
				noEqRF = noEqRF + 1;
				String identifier3 = getID(d3Model, resRF);
				Resource rsrce = pgModel.createResource(
						(ConstructURI.build("rf", identifier3, "F22_SelfContainedExpression")).toString());
				rsrce.addProperty(OWL.sameAs, pgModel.createResource(resRF));
			}
		}

		System.out.println("Triangles = " + surelinks);
		System.out.println("Conflits = " + linkstovalid);
		System.out.println("Liens uniques = " + createlinks);
		System.out.println("Plusieurs liens = " + plus);
		System.out.println("Liens inferres (sim 1) = " + inferredlinks);
		System.out.println("Deux liens : "+ twolinks);
		System.out.println("Liens gâchés = "+wasted);
		System.out.println("Liens totaux : "+(surelinks*3 + linkstovalid*3 + createlinks + plus + inferredlinks*2 + twolinks*2 + wasted));
		System.out.println("ressources PP uniques = " + noEqPP);
		System.out.println("ressources BNF uniques = " + noEqBNF);
		System.out.println("ressources RF uniques = " + noEqRF);
		System.out.println("TOTAL LINKS IN LINKLIST : "+linkList.size());

		FileWriter van = new FileWriter("store/pivotgraph.rdf");
		pgModel.write(van, "RDF/XML");
		van.close();
		
		/*FileWriter tou = new FileWriter("store/twolinkgraph.rdf");
		pgModel.write(tou, "RDF/XML");
		tou.close();
		
		FileWriter tri = new FileWriter("store/conflictgraph.rdf");
		pgModel.write(tri, "RDF/XML");
		tri.close();
		*/
		
		FileWriter foh = new FileWriter("store/plusgraph.rdf");
		plusModel.write(foh, "RDF/XML");
		foh.close();
		
		writeStats(stats);
		
		long endTime = System.currentTimeMillis();
		System.out.println("Total run time : " + (((double) (endTime - startTime)) / 1000.0));
		System.out.println("Model loading time : " + (((double) (modelTime - startTime)) / 1000));
		System.out.println("Link loading time : " + (((double) (linkTime - modelTime)) / 1000));
		System.out.println("Graph building time : " + (((double) (graphTime - linkTime)) / 1000));
		System.out.println("Bonus Graph Building time : " + (((double) (bonusGraphTime - graphTime)) / 1000));

	}

	public static boolean hasType(Resource resource) {
		// returns true if resource has a property (rdf:type) with a value
		// (classResource)
		boolean is = false;
		Model model = ModelFactory.createDefaultModel();
		Resource classResource = model.createResource("http://erlangen-crm.org/efrbroo/F22_Self-Contained_Expression");
		if (resource.hasProperty(RDF.type, classResource)) {
			is = true;
		}
		return is;
	}

	public static String getID(Model model, String rsrc) {
		String id = "";
		String sparqlQueryString = "PREFIX p1: <http://erlangen-crm.org/efrbroo/>"
				+ "PREFIX p2: <http://purl.org/dc/terms/>" + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
				+ "select ?id where {" + "<" + rsrc + "> a p1:F22_Self-Contained_Expression ." + "<" + rsrc
				+ "> p2:identifier ?id ." + "}";
		Query query = QueryFactory.create(sparqlQueryString);
		QueryExecution qexec = QueryExecutionFactory.create(query, model);
		ResultSet queryResults = qexec.execSelect();
		while (queryResults.hasNext()) {
			QuerySolution qs = queryResults.nextSolution();
			id = id + qs.getLiteral("?id").toString() + " ";
		}
		return id.trim();
	}

	/*****************************************************
	 * Get all resources of a given class from an RDF model
	 *****************************************************/
	public static List<Resource> getResources(Model model, List<String> classnames) {
		List<Resource> results = new ArrayList<Resource>();
		for (String classname : classnames) {
			String sparqlQueryString = "SELECT DISTINCT ?s { ?s a <" + classname + "> }";
			Query query = QueryFactory.create(sparqlQueryString);
			QueryExecution qexec = QueryExecutionFactory.create(query, model);
			ResultSet queryResults = qexec.execSelect();
			while (queryResults.hasNext()) {
				QuerySolution qs = queryResults.nextSolution();
				results.add(qs.getResource("?s"));
			}
			qexec.close();
		}
		return results;
	}

	/*
	 * adds a stat depending on its type and confidence value
	 */
	public static void addStat(StatBundle[] stats, String type, double conf) {
		stats[0].addStat(type);

		if (conf <= 0.3)
			stats[1].addStat(type);
		else if (conf <= 0.4)
			stats[2].addStat(type);
		else if (conf <= 0.5)
			stats[3].addStat(type);
		else if (conf <= 0.6)
			stats[4].addStat(type);
		else if (conf <= 0.7)
			stats[5].addStat(type);
		else if (conf <= 0.8)
			stats[6].addStat(type);
		else if (conf <= 0.9)
			stats[7].addStat(type);
		else
			stats[8].addStat(type);
	}
	
	public static void writeStats(StatBundle[] stats) throws IOException {
		
		FileWriter fw = new FileWriter("store/stats.txt");
		BufferedWriter bw = new BufferedWriter(fw);
		String tw = "";
		tw += "-----------------------------------------------------------------------------------\n";
		tw += "STATISTICS\n";
		tw += "-----------------------------------------------------------------------------------\n";
		tw += "\n";
		tw += "Total links : \n";
		tw += stats[0].toString()+"\n";
		tw += "-----------------------------------------------------------------------------------\n";
		tw += "Conf 0,2 - 0,3 : \n";
		tw += stats[1].toString()+"\n";
		tw += "-----------------------------------------------------------------------------------\n";
		tw += "Conf 0,31 - 0,4 : \n";
		tw += stats[2].toString()+"\n";
		tw += "-----------------------------------------------------------------------------------\n";
		tw += "Conf 0,41 - 0,5 : \n";
		tw += stats[3].toString()+"\n";
		tw += "-----------------------------------------------------------------------------------\n";
		tw += "Conf 0,51 - 0,6 : \n";
		tw += stats[4].toString()+"\n";
		tw += "-----------------------------------------------------------------------------------\n";
		tw += "Conf 0,61 - 0,7 : \n";
		tw += stats[5].toString()+"\n";
		tw += "-----------------------------------------------------------------------------------\n";
		tw += "Conf 0,71 - 0,8 : \n";
		tw += stats[6].toString()+"\n";
		tw += "-----------------------------------------------------------------------------------\n";
		tw += "Conf 0,81 - 0,9 : \n";
		tw += stats[7].toString()+"\n";
		tw += "-----------------------------------------------------------------------------------\n";
		tw += "Conf 0,91 - 1.0 : \n";
		tw += stats[8].toString()+"\n";
		tw += "-----------------------------------------------------------------------------------\n";
		
		bw.write(tw);
		bw.close();
		fw.close();
	}
}








