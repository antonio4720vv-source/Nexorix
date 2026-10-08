package com.nexorix.auth;

/** En que paso del inicio de sesion va la persona. */
public enum LoginStage {

    /** Debe escribir su PIN de 6 digitos. */
    PIN,

    /** Ya verifico su identidad pero aun no tiene PIN: debe crearlo. */
    CREATE_PIN,

    /** Sesion iniciada. */
    DONE
}
