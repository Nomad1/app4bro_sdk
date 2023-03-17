package net.runserver.apps4bro;

public interface AdNetworkHandler
{
	boolean show(Object data);
	
	void hide();

	void setId(String id);

	String getNetwork();
}
