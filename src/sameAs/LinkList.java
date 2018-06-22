package sameAs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.jena.rdf.model.Property;

public class LinkList {
	
	private final List<Link> links;
	
	public LinkList()
	{
		links = new ArrayList<Link>();
	}

	public void add (String uri1, String uri2, double rel) throws IOException 
	{
		Link link = new Link (uri1, uri2, rel);
		add(link);
	}
	
	public void add(Link link) 
	{ 
		links.add(link);
	}
	
	public Link get(int index) 
	{
		return links.get(index);
	}
	
	public Iterator<Link> iterator() 
	{
		return links.iterator();
	}
	
	public int size() 
	{
		return links.size(); 
	}
	
	@Override
	public String toString() 
	{
		StringBuilder sb = new StringBuilder();
		for (Link link : links) {
			sb.append(link.toString());
			sb.append("\n");
		}
		return sb.toString();
	}
	
	public Boolean existLink (String uri1, String uri2)
	{
		Boolean exist = false;
		for (Link link : links) {
			if (link.getObj1().equals(uri1) && link.getObj2().equals(uri2)) exist=true;
		}
		return exist;
	}
	
	public int getLinkID(String uri1, String uri2) {
		for(int i=0; i<links.size(); i++) {
			if(links.get(i).getObj1().equals(uri1) && links.get(i).getObj2().equals(uri2)) {
				return i;
			}
		}
		return -1;
	}
	
	public List<String> getObj2OfURI1(String uri1)
	{
		List<String> obj2 = new ArrayList<String>();
		for (Link link : links) {
			if (link.getObj1().equals(uri1))  obj2.add(link.getObj2());
		}
		return obj2;
	}
	
	public List<String> getObj1OfURI2(String uri2)
	{
		List<String> obj1 = new ArrayList<String>();
		for (Link link : links) {
			if (link.getObj2().equals(uri2))  obj1.add(link.getObj1());
		}
		return obj1;
	}
	
	public double getSimScore (String uri1, String uri2)
	{
		double sim=0;
		for (Link link : links) {
			if (link.getObj1().equals(uri1) & link.getObj2().equals(uri2))  sim=link.getScore();
		}
		return sim;
	}
	
	public boolean hasEquivalence (String uri)
	{
		boolean hasEq = false;
		for (Link link : links) {
			if (link.getObj1().equals(uri) || link.getObj2().equals(uri))  hasEq=true;
		}
		return hasEq; 
	}
	
	public int getNbEquivalences (String uri) {
		int i = 0;
		for(Link link : links) {
			if (link.getObj1().equals(uri) || link.getObj2().equals(uri)) i++;
		}
		return i;
	}
	
	public List<Link> getEquivalences (String uri) {
		ArrayList<Link> eq = new ArrayList<>();
		for(Link link : links) {
			if(link.getObj1().equals(uri) || link.getObj2().equals(uri)) eq.add(link);
		}
		return eq;
	}
}
