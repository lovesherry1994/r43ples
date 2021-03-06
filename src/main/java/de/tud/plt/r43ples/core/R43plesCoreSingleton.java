package de.tud.plt.r43ples.core;

import org.apache.log4j.Logger;

/**
 * The R43ples core singleton.
 *
 * @author Stephan Hensel
 */
public class R43plesCoreSingleton {

    /** The logger */
    private static Logger logger = Logger.getLogger(R43plesCoreSingleton.class);

    /** The R43ples core object. **/
    private static R43plesCoreInterface r43plesCore;


    /**
     * The constructor.
     */
    private R43plesCoreSingleton() {

    }

    /**
     * Get the instance of the currently chosen path calculation.
     *
     * @return the instance of the path calculation implementation
     */
    public static R43plesCoreInterface getInstance() {
        if (r43plesCore!=null)
            return r43plesCore;
        else {
            r43plesCore = new R43plesCore();
            return r43plesCore;
        }
    }

}
