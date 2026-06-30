grammar PlantUMLGantt;

@header {
    package com.ganttlens.parser;
}

// ========== Entry Point ==========
ganttFile
    : STARTGANTT (directive | task)* ENDGANTT
    ;

// ========== Directives ==========
directive
    : weekendsCloseDirective
    | holidayCloseDirective
    | personOffDirective
    | printscaleDirective
    | titleDirective
    ;

weekendsCloseDirective
    : SATURDAY_CLOSE
    | SUNDAY_CLOSE
    ;

holidayCloseDirective
    : DATE_TOKEN IS_CLOSED
    ;

personOffDirective
    : LBRACE personName RBRACE IS_OFF ON DATE_TOKEN
    ;

printscaleDirective
    : PRINTSCALE (WEEKLY | DAILY | MONTHLY)
    ;

titleDirective
    : TITLE WORD+
    ;

// ========== Tasks ==========
task
    : taskGroup? taskBody
    ;

taskGroup
    : DASH WORD+ DASH
    ;

taskBody
    : LBRACK taskName RBRACK taskResource? taskTiming
    ;

taskResource
    : ON resourceList
    ;

resourceList
    : resource resource*
    ;

resource
    : LBRACE personName (COLON ratio)? RBRACE
    ;

taskTiming
    : requiresClause startsAtClause?
    | startsAtClause requiresClause?
    ;

requiresClause
    : REQUIRES duration
    ;

startsAtClause
    : STARTS_AT startDate
    ;

duration
    : INTEGER DAYS
    ;

startDate
    : DATE_TOKEN
    | taskRef S_END
    | taskRef S_START
    ;

taskRef
    : LBRACK taskName RBRACK
    ;

// ========== Shared ==========
taskName
    : WORD+
    ;

personName
    : WORD+
    ;

ratio
    : INTEGER PERCENT
    ;

// ========== Lexer Rules ==========
STARTGANTT   : '@startgantt' ;
ENDGANTT     : '@endgantt' ;
SATURDAY_CLOSE : 'saturday' WS1 'are' WS1 'closed' ;
SUNDAY_CLOSE : 'sunday' WS1 'are' WS1 'closed' ;
IS_CLOSED    : 'is' WS1 'closed' ;
IS_OFF       : 'is' WS1 'off' ;
ON           : 'on' ;
REQUIRES     : 'requires' ;
STARTS_AT    : 'starts' WS1 'at' ;
S_END        : APOS 's' WS1 'end' ;
S_START      : APOS 's' WS1 'start' ;
PRINTSCALE   : 'printscale' ;
TITLE        : 'title' ;
WEEKLY       : 'weekly' ;
DAILY        : 'daily' ;
MONTHLY      : 'monthly' ;
DAYS         : 'days' ;
PERCENT      : '%' ;
COLON        : ':' ;
LBRACE       : '{' ;
RBRACE       : '}' ;
LBRACK       : '[' ;
RBRACK       : ']' ;
DASH         : '--' ;

DATE_TOKEN
    : DIGIT DIGIT DIGIT DIGIT '-' DIGIT DIGIT '-' DIGIT DIGIT
    ;

INTEGER
    : DIGIT+
    ;

fragment DIGIT : [0-9] ;
fragment APOS  : '\'' ;
fragment WS1   : [ \t]+ ;

WORD
    : ~[\] \t\r\n{}()[:%]+
    ;

NEWLINE
    : [\r\n]+
    -> skip
    ;

WS
    : [ \t]+
    -> skip
    ;
