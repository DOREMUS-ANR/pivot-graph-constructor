package pivotgraph;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.OWL;
import org.omg.Messaging.SyncScopeHelper;
import org.semanticweb.owl.align.Alignment;
import org.semanticweb.owl.align.AlignmentException;
import org.semanticweb.owl.align.Cell;

import fr.inrialpes.exmo.align.parser.AlignmentParser;
import sameAs.Link;
import sameAs.LinkList;

public class PivotGraph {

	public static void main(String[] args) throws AlignmentException, IOException, URISyntaxException {
		long startTime = System.currentTimeMillis();
		
		//-------------------------------------SORTING PHASE---------------------------------------
		int nbSure = 0;		//number of sure links
		int nbInvalid = 0;	//number of invalid links
		int nbInferred = 0;	//number of invalid links
		double threshold;	//conf value, in some cases if a link has a value superior or equal to it, it will be considered a sure link
	
		if(args.length==0) {
			System.err.println("No threshold input. Setting default value : 1.0");
			threshold = 1.0d;
		} else {
			try {
				threshold = Double.parseDouble(args[0]);
			} catch(NumberFormatException ex) {
				System.err.println("Your threshold value is not a number. Setting default value : 1.0");
				threshold = 1.0d;
			}
		}
		
		ArrayList<Link> treated = new ArrayList<>();	//treated links, so we do not process again a link we have already covered 
		ArrayList<Link> written = new ArrayList<>();	//written links, so we do not write a link that we have already written
		System.out.println("Threshold = "+threshold);
		
		//building a list of links containing all links in finalresults
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
				
		System.out.println("Sorting valid and unsure links...");
		//file for triangles and inferred links
		FileWriter alignFile= new FileWriter ("store/surelinks.rdf");
		BufferedWriter bw = new BufferedWriter (alignFile);
		PrintWriter pw = new PrintWriter (bw);
		pw.println("<?xml version='1.0' encoding='utf-8'?>");
		pw.println("<rdf:RDF xmlns='http://knowledgeweb.semanticweb.org/heterogeneity/alignment'");
		pw.println("xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns#'");
		pw.println("xmlns:xsd='http://www.w3.org/2001/XMLSchema#'>");
		pw.println("<Alignment>");
		pw.println("<xml>yes</xml>");
		pw.println("<level>0</level>");
		pw.println("<type>??</type>");

		//file for coda (all other links that need validation)
		FileWriter codafile = new FileWriter ("store/tovalidate.rdf");
		BufferedWriter bw_coda = new BufferedWriter (codafile);
		PrintWriter pw_coda = new PrintWriter (bw_coda);
		pw_coda.println("<?xml version='1.0' encoding='utf-8'?>");
		pw_coda.println("<rdf:RDF xmlns='http://knowledgeweb.semanticweb.org/heterogeneity/alignment'");
		pw_coda.println("xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns#'");
		pw_coda.println("xmlns:xsd='http://www.w3.org/2001/XMLSchema#'>");
		pw_coda.println("<Alignment>");
		pw_coda.println("<xml>yes</xml>");
		pw_coda.println("<level>0</level>");
		pw_coda.println("<type>??</type>");
		
		int cpt = 0;	//loop counter
		/*
		 * we iterate on all the links
		 * from a link, a have a linked to b, a-b
		 * we get all the links going from something to a and from b to something
		 * if both links exist and are the same, it is a triangle ( a-b-c-a )
		 * if there is two links but no third, first verify that it is not a conflict
		 *   if not a conflict, we try to infer a third link if the conf score is high enough
		 *   if no conf scores are too low, we pass them on to coda as two-links
		 * any other case, we just pass it on to coda
		 */
		
		
		for(int i=0; i<linkList.size(); i++) { //for each link																																																																																																																																																																																		
			cpt++;
			Link curr = linkList.get(i);
			if(!treated.contains(curr)) {	//verify we haven't treated it already (in a triangle for example)
				treated.add(curr);
				List<String> linkTo = linkList.getObj2OfURI1(curr.getObj2());	//link from curr to something
				List<String> linkFrom = linkList.getObj1OfURI2(curr.getObj1());	//link from something to curr
				for(String s : linkTo) {
					if(!treated.contains(getLinkFromUris(linkList, curr.getObj2(),s)))
						treated.add(getLinkFromUris(linkList, curr.getObj2(),s));
				}
				for(String s : linkFrom) {
					if(!treated.contains(getLinkFromUris(linkList, s, curr.getObj1())))
						treated.add(getLinkFromUris(linkList, s, curr.getObj1()));
				}
				if(linkTo.size()==1 && linkFrom.size()==1) {
					Link l_linkto = getLinkFromUris(linkList, curr.getObj2(), linkTo.get(0));
					Link l_linkfrom = getLinkFromUris(linkList, linkFrom.get(0), curr.getObj1());
					if(linkTo.get(0).equals(linkFrom.get(0))) {
						//triangle, everything is sure
						nbSure = nbSure+writeLink(pw, curr, written);
						nbSure = nbSure+writeLink(pw, l_linkto, written);																																																																																																																																																																																											
						nbSure = nbSure+writeLink(pw, l_linkfrom, written);
					} else { 
						//conflict c-c'
						//in a conflict, everything but the sure links (conf==1) must be validated manually	
						if(curr.getScore()==1) 
							nbSure = nbSure+writeLink(pw, curr, written);	
						else
							nbInvalid=nbInvalid+writeLink(pw_coda, curr, written);
						if(l_linkto.getScore()==1) 
							nbSure = nbSure+writeLink(pw, l_linkto, written);
						else 
							nbInvalid=nbInvalid+writeLink(pw_coda, l_linkto, written);
						if(l_linkfrom.getScore()==1)
							nbSure = nbSure+writeLink(pw, l_linkfrom, written);
						else 
							nbInvalid=nbInvalid+writeLink(pw_coda, l_linkfrom, written);
					}
				} else if((linkTo.size()==1 && linkFrom.isEmpty()) || (linkTo.isEmpty() && linkFrom.size() == 1)) { 			
					if(linkFrom.isEmpty()) {
						//there is no link going to our resource a from c, but maybe there is a conflict
						//meaning there might be a link going from the c we just found to another a
						List<String> linkCtoA = linkList.getObj2OfURI1(linkTo.get(0)); 
						for(String s : linkCtoA) {
							if(!treated.contains(getLinkFromUris(linkList, linkTo.get(0), s))) 
								treated.add(getLinkFromUris(linkList, linkTo.get(0),s));	
						}
						if(linkCtoA.isEmpty()) {
							//no conflict
							if(getLinkFromUris(linkList, curr.getObj2(), linkTo.get(0)).getScore() >= threshold && curr.getScore()>= threshold) {
								//inferrence
								nbInferred++;
								nbSure = nbSure+writeLink(pw, curr, written);
								nbSure = nbSure+writeLink(pw, getLinkFromUris(linkList, curr.getObj2(), linkTo.get(0)), written);
								writeLink(pw, linkTo.get(0), curr.getObj1());
							} else {
								//two links
								//if conf score is superior or equal to threshold we consider them sure
								if(curr.getScore()>=threshold) 
									nbSure = nbSure+writeLink(pw, curr, written);
								else
									nbInvalid=nbInvalid+writeLink(pw_coda, curr, written);
								if(getLinkFromUris(linkList, curr.getObj2(), linkTo.get(0)).getScore()>=threshold) 
									nbSure = nbSure+writeLink(pw, getLinkFromUris(linkList, curr.getObj2(), linkTo.get(0)), written);
								else 
									nbInvalid=nbInvalid+writeLink(pw_coda, getLinkFromUris(linkList, curr.getObj2(), linkTo.get(0)), written);
							}
						} else if(linkCtoA.size()==1){
							//conflict a-a'
							Link l_linkto = getLinkFromUris(linkList, curr.getObj2(), linkTo.get(0));
							Link l_linkCtoA = getLinkFromUris(linkList, linkTo.get(0), linkCtoA.get(0));
							if(curr.getScore()==1)
								nbSure = nbSure+writeLink(pw, curr, written);
							else 
								nbInvalid=nbInvalid+writeLink(pw_coda, curr, written);
							if(l_linkto.getScore()==1)
								nbSure = nbSure+writeLink(pw, l_linkto, written);
							else
								nbInvalid=nbInvalid+writeLink(pw_coda, l_linkto, written);
							if(l_linkCtoA.getScore()==1)
								nbSure = nbSure+writeLink(pw, l_linkCtoA, written);
							else
								nbInvalid=nbInvalid+writeLink(pw_coda, l_linkCtoA, written);
							
						} else {
							//multiple links problems
							//write current link
							if(curr.getScore()==1)
								nbSure = nbSure+writeLink(pw, curr, written);
							else 
								nbInvalid=nbInvalid+writeLink(pw_coda, curr, written); 
							
							//write link from b to c
							Link l_linkto = getLinkFromUris(linkList, curr.getObj2(), linkTo.get(0));
							if(l_linkto.getScore()==1) 
								nbSure = nbSure+writeLink(pw, l_linkto, written);
							else 
								nbInvalid=nbInvalid+writeLink(pw_coda, l_linkto, written);
							
							//write links from c to other a's
							for(String s : linkCtoA) {
								Link l_linkCtoA = getLinkFromUris(linkList, linkTo.get(0), s);
								if(l_linkCtoA.getScore()==1)
									nbSure = nbSure+writeLink(pw, l_linkCtoA, written);
								else
									nbInvalid=nbInvalid+writeLink(pw_coda, l_linkCtoA, written);
							}
						}
					} else {	//linkTo.isEmpty()
						//there is no link going from our resource b to c, but maybe there is a conflict
						//meaning there might be a link going to the c we just found from another b
						List<String> linkCtoB = linkList.getObj1OfURI2(linkFrom.get(0)); 
						for(String s : linkCtoB) {
							if(!treated.contains(getLinkFromUris(linkList, s, linkFrom.get(0))))
								treated.add(getLinkFromUris(linkList, s, linkFrom.get(0)));
						}
						if(linkCtoB.isEmpty()) {
							//no conflict
							if(getLinkFromUris(linkList, linkFrom.get(0), curr.getObj1()).getScore() >= threshold && curr.getScore()>=threshold) {
								//inferrence
								nbInferred++;
								nbSure = nbSure+writeLink(pw, curr, written);
								nbSure = nbSure+writeLink(pw, getLinkFromUris(linkList, linkFrom.get(0), curr.getObj1()), written);
								writeLink(pw, curr.getObj2(), linkFrom.get(0));
							} else {
								//two links
								if(curr.getScore()>=threshold) 
									nbSure = nbSure+writeLink(pw, curr, written);	
								else 
									nbInvalid=nbInvalid+writeLink(pw_coda, curr, written);
								if(getLinkFromUris(linkList, linkFrom.get(0), curr.getObj1()).getScore()>=threshold) 
									nbSure = nbSure+writeLink(pw, getLinkFromUris(linkList, linkFrom.get(0), curr.getObj1()), written);
								else
									nbInvalid=nbInvalid+writeLink(pw_coda, getLinkFromUris(linkList, linkFrom.get(0), curr.getObj1()), written);
								
							}
						} else if (linkCtoB.size()==1){
							//conflict b-b'
							Link l_linkfrom = getLinkFromUris(linkList, linkFrom.get(0), curr.getObj1());
							Link l_linkCtoB = getLinkFromUris(linkList, linkCtoB.get(0), linkFrom.get(0));
							if(curr.getScore()==1)
								nbSure = nbSure+writeLink(pw, curr, written);
							else
								nbInvalid=nbInvalid+writeLink(pw_coda, curr, written);
							if(l_linkfrom.getScore()==1) 
								nbSure = nbSure+writeLink(pw, l_linkfrom, written);
							else 
								nbInvalid=nbInvalid+writeLink(pw_coda, l_linkfrom, written);
							if(l_linkCtoB.getScore()==1) 
								nbSure = nbSure+writeLink(pw, l_linkCtoB, written);
							else 
								nbInvalid=nbInvalid+writeLink(pw_coda, l_linkCtoB, written);
						} else {
							//linked to multiple links. to validate.
							if(curr.getScore()==1) {
								nbSure = nbSure+writeLink(pw, curr, written);
							} else {
								nbInvalid=nbInvalid+writeLink(pw_coda, curr, written);
							}
							Link l_linkfrom = getLinkFromUris(linkList, linkFrom.get(0), curr.getObj1());
							if(l_linkfrom.getScore()==1) {
								nbSure = nbSure+writeLink(pw, l_linkfrom, written);
							} else {
								nbInvalid=nbInvalid+writeLink(pw_coda, l_linkfrom, written);
							}
							for(String s : linkCtoB) {
								Link l_linkCtoB = getLinkFromUris(linkList, s, linkFrom.get(0));
								if(l_linkCtoB.getScore()==1) {
									nbSure = nbSure+writeLink(pw, l_linkCtoB, written);
								} else {
									nbInvalid=nbInvalid+writeLink(pw_coda, l_linkCtoB, written);
								}
							}
						}
					}
				} else {// single links or multiples links
					if(linkList.getNbEquivalences(curr.getObj1())==1 && linkList.getNbEquivalences(curr.getObj2())==1) {
						//single link 
						if(curr.getScore()>=threshold) {
							nbSure = nbSure+writeLink(pw, curr, written);
						} else {
							nbInvalid=nbInvalid+writeLink(pw_coda, curr, written);
						}
					} else {
						//uncharted territory
						//multiple links, rare, large and complex multistructures
						//here be dragons
						//we only write them in sure if they are absolutely secure (conf==1)
						if(curr.getScore()==1) {
							nbSure = nbSure+writeLink(pw, curr, written);
						} else {
							nbInvalid=nbInvalid+writeLink(pw_coda, curr, written);
						}
						for(String s : linkTo) {
							Link l_linkto = getLinkFromUris(linkList, curr.getObj2(), s);
							if(l_linkto.getScore()==1) {
								nbSure = nbSure+writeLink(pw, l_linkto, written);
							} else {
								nbInvalid=nbInvalid+writeLink(pw_coda, l_linkto, written);
							}
						}
						for(String s : linkFrom) {
							Link l_linkfrom = getLinkFromUris(linkList, s, curr.getObj1());
							if(l_linkfrom.getScore()==1) {
								nbSure = nbSure+writeLink(pw, l_linkfrom, written);
							} else {
								nbInvalid=nbInvalid+writeLink(pw_coda, l_linkfrom, written);
							}
						}
					}
				}
			}
		}
		
		//end of the surelinks file
		pw.println("</Alignment>");
		pw.println("</rdf:RDF>");
		pw.close();
		bw.close();
		alignFile.close();		
		//we don't write the end of the tovalid file yet, since we can still find special cases in the pivot graph construction

		long sortTime = System.currentTimeMillis();
		
		Set<Link> set = new HashSet<Link>(treated);
		if(set.size()<treated.size()) System.out.println(treated.size()-set.size()+" duplicates in treated");

		System.out.println("Sure links      : "+nbSure);
		System.out.println("To Validate     : "+nbInvalid);
		System.out.println("Total Written Links  : "+(written.size()+nbInferred)+"  ("+cpt+" original links, "+nbInferred+" inferred links)");
		
		
		//-------------------------------------MODEL LOADING---------------------------------------
		System.out.println("");
		System.out.println("Loading models...");
		System.out.println("Warning : this step can take a long time, depending on the size of your models.");
		Model aModel = ModelFactory.createDefaultModel();	//base A model (loading from file)
		Model bModel = ModelFactory.createDefaultModel();	//base B model (same)
		Model cModel = ModelFactory.createDefaultModel();	//base C model (same)
		Model pgModel = ModelFactory.createDefaultModel();	//pivot graph (that we are going to build)
		InputStream aIn = new FileInputStream(new File("store/pp.ttl"));
		InputStream bIn = new FileInputStream(new File("store/bnf_f22only.ttl"));
		InputStream cIn = new FileInputStream(new File("store/rf.ttl"));
		
		aModel.read(aIn, null, "TTL"); // pp
		String aid = "pp";
		System.out.println("Model 1 loaded, ("+aid+")");
		
		bModel.read(bIn, null, "TTL"); // bnf
		String bid = "bnf";
		System.out.println("Model 2 loaded, ("+bid+")");
		
		//InputStream in =new FileInputStream(inputFile);
		Reader r = new InputStreamReader(cIn, Charset.forName("UTF-8"));

		cModel.read(r,null,"TTL");
		
	//	cModel.read(cIn, null, "TTL"); // rf
		String cid = "rf";
		System.out.println("Model 3 loaded, ("+cid+")");
		long modelTime = System.currentTimeMillis();
		
		System.out.println("Valid Links loading ...");
		AlignmentParser aparser2 = new AlignmentParser(0);
		Alignment results2 = aparser2.parse(new File("store/surelinks.rdf").toURI());
		List<Cell> list2 = Collections.list(results2.getElements());
		LinkList sureLinkList = new LinkList();
		for (Cell cell : list2) {
			Link link = new Link(cell.getObject1().toString(), cell.getObject2().toString(), cell.getStrength());
			sureLinkList.add(link);
		}
		System.out.println("Total sure links loaded : "+sureLinkList.size());
		
		System.out.println("PivotGraph construction...");
		treated = new ArrayList<Link>();			//reseting treated 
		ArrayList<Link> vsc = new ArrayList<>(); 	//to list very special cases (ex : multiple links with conf 1 that passed sorting)
		int sureTriangle = 0;
		int sureSingle = 0;
		
		/*
		 * these are sure links, there is supposed to be only two scenarios : 
		 * single link or triangle
		 * we determine which case it is, then create the resources and add em to the model
		 * it is possible to detect very special cases, that we are going to add to another file, given their rarity and complexity 
		 * (they managed to cheat sorting after all)
		 */
		for(int i=0; i<sureLinkList.size(); i++) {
			Link curr = sureLinkList.get(i);
			if(!treated.contains(curr)) {
				treated.add(curr);
				
				if(sureLinkList.getNbEquivalences(curr.getObj1())==1 && sureLinkList.getNbEquivalences(curr.getObj2())==1) {
					//single link
					sureSingle++;
					List<String> identifiers1 = getIDFromModels(aModel, bModel, cModel, curr.getObj1(), aid, bid, cid);
					List<String> identifiers2 = getIDFromModels(aModel, bModel, cModel, curr.getObj2(), aid, bid, cid);
					Resource rsrce = pgModel.createResource((ConstructURI.build(identifiers1.get(0), identifiers1.get(1), 
							identifiers2.get(0), identifiers2.get(1), "F22_SelfContainedExpression")).toString());
					rsrce.addProperty(OWL.sameAs, pgModel.createResource(curr.getObj1()));
					rsrce.addProperty(OWL.sameAs, pgModel.createResource(curr.getObj2()));
				} else {
					//triangle or very special case (multiple conf 1 links for example)
					List<String> linkTo = sureLinkList.getObj2OfURI1(curr.getObj2());
					List<String> linkFrom = sureLinkList.getObj1OfURI2(curr.getObj1());
					if(linkTo.isEmpty() || linkFrom.isEmpty()) {
						//neither a single link nor a triangle : there is a problem
						vsc.add(curr);
					} else {
						//triangle	
						sureTriangle = sureTriangle +3;
						treated.add(getLinkFromUris(sureLinkList, linkTo.get(0), curr.getObj1()));
						treated.add(getLinkFromUris(sureLinkList, curr.getObj2(), linkTo.get(0)));
						List<String> identifiers1 = getIDFromModels(aModel, bModel, cModel, curr.getObj1(), aid, bid, cid);
						List<String> identifiers2 = getIDFromModels(aModel, bModel, cModel, curr.getObj2(), aid, bid, cid);
						List<String> identifiers3 = getIDFromModels(aModel, bModel, cModel, linkTo.get(0), aid, bid, cid);
						Resource rsrce = pgModel.createResource((ConstructURI.build(identifiers1.get(0), identifiers1.get(1), 
							identifiers2.get(0), identifiers2.get(1), identifiers3.get(0), identifiers3.get(1),
							"F22_SelfContainedExpression")).toString());
						rsrce.addProperty(OWL.sameAs, pgModel.createResource(curr.getObj1()));
						rsrce.addProperty(OWL.sameAs, pgModel.createResource(curr.getObj2()));
						rsrce.addProperty(OWL.sameAs, pgModel.createResource(linkTo.get(0)));
					}
				}
			}
		}
		long pivotTime = System.currentTimeMillis();
		
		//creating a pivot uri for each unlinked resource
		System.out.println("Finding unlinked resources...");
		List<String> classnames = new ArrayList<String>();
		classnames.add("http://erlangen-crm.org/efrbroo/F22_Self-Contained_Expression");

		int noEqA = 0;
		int noEqB = 0;
		int noEqC = 0;
		
		List<Resource> aRsrces = getResources(aModel, classnames);
		for (Resource res : aRsrces) {
			String resA = res.toString();
			if (sureLinkList.hasEquivalence(resA) == false) {
				noEqA = noEqA + 1;
				List<String> identifiers1 = getIDFromModels(aModel, bModel, cModel, resA, aid, bid, cid);
				Resource rsrce = pgModel.createResource(
						(ConstructURI.build(identifiers1.get(0), identifiers1.get(1), "F22_SelfContainedExpression")).toString());
				rsrce.addProperty(OWL.sameAs, pgModel.createResource(resA));
			}
		}

		List<Resource> bRsrces = getResources(bModel, classnames);
		for (Resource res : bRsrces) {
			String resB = res.toString();
			if (sureLinkList.hasEquivalence(resB) == false) {
				noEqB = noEqB + 1;
				List<String> identifiers2 = getIDFromModels(aModel, bModel, cModel, resB, aid, bid, cid);
				Resource rsrce = pgModel.createResource(
						(ConstructURI.build(identifiers2.get(0), identifiers2.get(1), "F22_SelfContainedExpression")).toString());
				rsrce.addProperty(OWL.sameAs, pgModel.createResource(resB));
			}
		}

		List<Resource> cRsrces = getResources(cModel, classnames);
		for (Resource res : cRsrces) {
			String resC = res.toString();
			if (sureLinkList.hasEquivalence(resC) == false) {
				noEqC = noEqC + 1;
				List<String> identifiers3 = getIDFromModels(aModel, bModel, cModel, resC, aid, bid, cid);
				Resource rsrce = pgModel.createResource(
						(ConstructURI.build(identifiers3.get(0), identifiers3.get(1), "F22_SelfContainedExpression")).toString());
				rsrce.addProperty(OWL.sameAs, pgModel.createResource(resC));
			}
		}
		long unicTime = System.currentTimeMillis();
		
		//writing PGC
		System.out.println("Writing results...");
		FileWriter van = new FileWriter("store/pivotgraph.rdf");
		pgModel.write(van, "RDF/XML");
		van.close();
		
		//writing special cases, both in the VSC file and the tovalid file								
		FileWriter tvo = new FileWriter("store/VSC.rdf");
		BufferedWriter bwtvo = new BufferedWriter(tvo);
		PrintWriter pwtvo = new PrintWriter(bwtvo);
		for(Link l : vsc) {	
			writeLink(pwtvo, l, new ArrayList<>());		//no need to give a written arraylist, we know these links 
			writeLink(pw_coda, l, new ArrayList<>());	//have not been written already
		}					
		
		//finally closing the tovalid file
		pw_coda.println("</Alignment>");
		pw_coda.println("</rdf:RDF>");
		pw_coda.close();
		bw_coda.close();
		codafile.close();
		
		System.out.println("");	
		System.out.println("-------------------------------------------------------------------------------------------------");
		System.out.println("RESULTS");
		System.out.println("-------------------------------------------------------------------------------------------------");
		System.out.println("Sure Single Links : "+sureSingle);
		System.out.println("Sure Triangles : "+sureTriangle);
		System.out.println("Special Cases detected : "+vsc.size());
		System.out.println("Total : "+(sureSingle+sureTriangle+vsc.size()));
		System.out.println("Unique resources from "+aid+" : "+noEqA);
		System.out.println("Unique resources from "+bid+" : "+noEqB);
		System.out.println("Unique resources from "+cid+" : "+noEqC);
		long endTime = System.currentTimeMillis();
		System.out.println("Total run time : .............."+(endTime-startTime));
		System.out.println("Sorting : ....................."+(sortTime-startTime));
		System.out.println("Model Loading : ..............."+(modelTime-sortTime));
		System.out.println("Pivot Graph generation : ......"+(pivotTime-modelTime));
		System.out.println("Unique Resources handling : ..."+(unicTime-pivotTime));
		
		
		
	}
	
	/**
	 * writes a link with pw if it is not written already
	 * 
	 * @param pw : printwriter used to write the link
	 * @param l : link that is going to be written
	 * @param written : list containing all links already written, so we do not write a link twice
	 * @return 1 if the link was written, 0 if it was already present in the list written
	 *
	 */
	public static int writeLink(PrintWriter pw, Link l, List<Link> written) {
		if(!written.contains(l)) {
			pw.println("<map>");
			pw.println("<Cell>");
			pw.println("<entity1 rdf:resource=\""+l.getObj1()+"\"/>");
			pw.println("<entity2 rdf:resource=\""+l.getObj2()+"\"/>");
			pw.println("<measure rdf:datatype=\"http://www.w3.org/2001/XMLSchema#float\">"+l.getScore()+"</measure>");
			pw.println("<relation> = </relation>");
			pw.println("</Cell>");
			pw.println("</map>");
			pw.println("");
			written.add(l);
			return 1;
		} else return 0;
	}
	
	/**
	 * used to write links that are not present in finalResults
	 * e.g. to write links deduced from inferrences
	 * no need to give a conf value, these links are always written with a conf of 1
	 * 
	 * @param pw : printwriter used to write the link
	 * @param uri1 : the first uri of the link
	 * @param uri 2 : the seconde uri of the link
	 * 
	 */
	public static void writeLink(PrintWriter pw, String uri1, String uri2) {	
		pw.println("<map>");
		pw.println("<Cell>");
		pw.println("<entity1 rdf:resource=\""+uri1+"\"/>");
		pw.println("<entity2 rdf:resource=\""+uri2+"\"/>");
		pw.println("<measure rdf:datatype=\"http://www.w3.org/2001/XMLSchema#float\">"+1.0+"</measure>");
		pw.println("<relation> = </relation>");
		pw.println("</Cell>");
		pw.println("</map>");
		pw.println("");
	}
	
	/**
	 * gets a specific Link from the LinkList, if it exists
	 * yes, this method could be written in LinkList instead
	 * 
	 * @param l : the list from where we will search the link 
	 * @param uri1 : first uri of the link we want
	 * @param uri2 : second uri - - - - - - - - - -
	 * @return the link we searched for if it exists, null if not
	 */
	public static Link getLinkFromUris(LinkList l, String uri1, String uri2) {
		int i = l.getLinkID(uri1, uri2);
		if(i>=0) {
			Link lin = l.get(i);
			return lin;
		} else return null;
	}
	
	//finds from which base a resource is, and its id
	
	/**
	 * finds from which base a resource is from, and its id
	 * 
	 * @param amodel : first model (base)
	 * @param bmodel : second model (base)
	 * @param cmodel : third model (base)
	 * @param rsrc : the resource we want to find (the uri)
	 * @param aid : identifier of the first base (default version : pp)
	 * @param bid : identifier of the second base (default version : bnf)
	 * @param cid : identifier of the third base (default version : rf)
	 * @return a list containing in this order : - the identifier of the model where the rsrc was found
	 *             								 - the id of the resource in said model
	 *         null if the resource was not found
	 */
	public static List<String> getIDFromModels(Model amodel, Model bmodel, Model cmodel, String rsrc, String aid, String bid, String cid) {
		String id = "";
		String sparqlQueryString = "PREFIX p1: <http://erlangen-crm.org/efrbroo/>"
				+ "PREFIX p2: <http://purl.org/dc/terms/>" + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
				+ "select ?id where {" + "<" + rsrc + "> a p1:F22_Self-Contained_Expression ." + "<" + rsrc
				+ "> p2:identifier ?id ." + "}";
		Query query = QueryFactory.create(sparqlQueryString);
		ArrayList<String> results = new ArrayList<>();
		//checking model a
		QueryExecution qexec = QueryExecutionFactory.create(query, amodel);
		ResultSet queryResults = qexec.execSelect();
		int nbResults = 0;
		while (queryResults.hasNext()) {
			nbResults++;
			QuerySolution qs = queryResults.nextSolution();
			id = id + qs.getLiteral("?id").toString() + " ";
		}
		if(nbResults>0) {
			results.add(aid);
			results.add(id.trim());
			return results;
		}
		
		//model b
		qexec = QueryExecutionFactory.create(query, bmodel);
		queryResults = qexec.execSelect();
		nbResults = 0;
		while (queryResults.hasNext()) {
			nbResults++;
			QuerySolution qs = queryResults.nextSolution();
			id = id + qs.getLiteral("?id").toString() + " ";
		}
		if(nbResults>0) {
			results.add(bid);
			results.add(id.trim());
			return results;
		}
		
		qexec = QueryExecutionFactory.create(query, cmodel);
		queryResults = qexec.execSelect();
		nbResults = 0;
		while (queryResults.hasNext()) {
			nbResults++;
			QuerySolution qs = queryResults.nextSolution();
			id = id + qs.getLiteral("?id").toString() + " ";
		}
		if(nbResults>0) {
			results.add(cid);
			results.add(id.trim());
			return results;
		}
		
		return null;
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
	
}
