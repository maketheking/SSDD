/*
 * AUTOR: Juan Vela y Marta Frias
 * NIA: 643821 - 535621
 * FICHERO: InBox.java
 * TIEMPO: 30 min
 * DESCRIPCION: Clase que proporciona un buzon de mensajes.
 */
package ssdd.p4.ms;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Clase que proporciona un buzon de mensajes con acceso en exclusion mutua (es
 * una cola de mensajes recibidos pero no leidos).
 * 
 * @author Juan Vela
 * @author Marta Frias
 *
 */
public class InBox {

	/** Buzon de mensajes */
	private LinkedList<Serializable> inBox;

	/** Tama�o maximo del buzon */
	private int maxSize;

	/** Candado */
	private final Lock mutex;

	/** Condicion buzon vacio */
	private final Condition empty;

	/**
	 * Crea un buzon de mensajes.
	 * 
	 * @param size
	 *            tama�o del buzon
	 */
	public InBox(int size) {

		maxSize = size;
		inBox = new LinkedList<Serializable>();
		mutex = new ReentrantLock();
		empty = mutex.newCondition();
	}

	/**
	 * Devuelve true si y solo si el buzon de mensajes esta vacio.
	 * 
	 * @return true si el buzon esta vacio
	 */
	public boolean isEmpty() {

		return inBox.isEmpty();
	}

	/**
	 * Devuelve true si y solo si el buzon de mensajes ha llegado a su capacidad
	 * maxima.
	 * 
	 * @return true si el buzon esta lleno
	 */
	public boolean isFull() {

		return inBox.size() == maxSize;
	}

	/**
	 * A�ade un mensaje al buzon mientras no este lleno. Si esta lleno, el
	 * mensaje es descartado.
	 * 
	 * @param msg
	 *            Objeto serializable que se a�adira al buzon
	 */
	public void addMsg(Serializable msg) {

		mutex.lock();

		if (!isFull()) {

			inBox.addLast(msg);
			empty.signal();
		}

		mutex.unlock();
	}

	/**
	 * <b>Bloqueante.</b><br/>
	 * <br/>
	 * Extrae el primer mensaje del buzon. Si esta vacio, el proceso que lo
	 * invoca se queda bloqueado hasta que se reciba algun mensaje.
	 * 
	 * @return Objeto Serializable con el contenido del mensaje.
	 * 
	 */
	public Serializable getMsg() {

		mutex.lock();

		Serializable result = null;

		while (isEmpty()) {
			try {
				empty.await();

			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		result = inBox.getFirst();
		inBox.removeFirst();

		mutex.unlock();

		return result;
	}

}
