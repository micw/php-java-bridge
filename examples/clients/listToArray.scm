#!/usr/bin/guile -s
!#

;; PHP/Java Bridge test client written in GUILE/Scheme.
;; Run this example with: guile -s listToArray.scm

(use-modules (ice-9 rw))

(define HOST "127.0.0.1")
(define PORT 9176)

(define buf (make-string 65535))
(define (error-handler key . args) 
  (display "Please start the bridge, for example with java -jar JavaBridge.jar 9176 5 \"\"")
  (newline)
  (exit 1))

(let ((s (socket AF_INET SOCK_STREAM 0)))

  (let ((connect (lambda () (connect s AF_INET (inet-aton HOST) PORT)))) 
    (catch #t connect error-handler))

  (display (list->string '(#\02)) s)	; we want arrays as values, see PROTOCOL.TXT

  ;; create a java.util.ArrayList, add 3 entries to it ...
  (display "<C value=\"java.util.ArrayList\" p=\"I\" id=\"0\"></C>" s)
  (read-string!/partial buf s)
  (display "<I value=\"1\" method=\"add\" p=\"I\" id=\"0\"><String v=\"ENTRY 1\"/></I>" s)
  (read-string!/partial buf s)
  (display "<I value=\"1\" method=\"add\" p=\"I\" id=\"0\"><String v=\"ENTRY 2\"/></I>" s)
  (read-string!/partial buf s)
  (display "<I value=\"1\" method=\"add\" p=\"I\" id=\"0\"><String v=\"LAST ENTRY\"/></I>" s)
  (read-string!/partial buf s)

  ;; ... and ask for the array
  (display "<I value=\"1\" m=\"toArray\" p=\"Invoke\" id=\"0\"></I>" s)
  (let ((count (read-string!/partial buf s))) ; System: object=1

    (display "Received:") (newline)
    ;; should have received an array of three values
    (display (substring buf 0 count))
    (newline)))