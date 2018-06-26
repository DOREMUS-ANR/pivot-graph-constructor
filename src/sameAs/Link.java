package sameAs;

import java.io.IOException;

public class Link {
	
	private String uri1;
	private String uri2;
	private double rel;

	public Link(String uri1, String uri2, double rel) throws IOException {
		this.uri1 = uri1;
		this.uri2 = uri2;
		this.rel = rel;
	}
	
	public String getObj1()
	{
		return uri1;
	}
	
	public String getObj2()
	{
		return uri2;
	}
	
	public double getScore()
	{
		return rel;
	}
	
	@Override
	public boolean equals(Object o) {
		if(o==null) return false;
		return (((Link) o).getObj1().equals(uri1) && ((Link) o).getObj2().equals(uri2) && ((Link) o).getScore()==rel);
	}
	
}
