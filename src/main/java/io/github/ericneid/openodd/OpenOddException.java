// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd;

/**
 * Base exception of the OpenODD library. Parsing, validation and evaluation
 * problems are reported through this unchecked exception type.
 */
public class OpenOddException extends RuntimeException {

	/**
	 * Creates an exception with a message.
	 *
	 * @param message the error message
	 */
	public OpenOddException(String message) {
		super(message);
	}

	/**
	 * Creates an exception with a message and cause.
	 *
	 * @param message the error message
	 * @param cause   the cause of the error
	 */
	public OpenOddException(String message, Throwable cause) {
		super(message, cause);
	}
}
