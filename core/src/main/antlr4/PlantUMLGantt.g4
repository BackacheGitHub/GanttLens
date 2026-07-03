grammar PlantUMLGantt;

@header {
    package com.ganttlens.parser;
}

// ========== Entry Point ==========
ganttFile
    : STARTGANTT (directive | taskGroupDirective | task | arrowDependency | thenTask | milestone)* ENDGANTT
    ;

// ========== Directives ==========
directive
    : weekendsCloseDirective
    | holidayCloseDirective
    | personOffDirective
    | printscaleDirective
    | titleDirective
    | projectStartDirective
    | dateRangeCloseDirective
    | dateOpenDirective
    ;

weekendsCloseDirective
    : SATURDAY_CLOSE
    | SUNDAY_CLOSE
    ;

holidayCloseDirective
    : DATE_TOKEN IS_CLOSED
    ;

dateRangeCloseDirective
    : DATE_TOKEN TO DATE_TOKEN IS_CLOSED
    ;

dateOpenDirective
    : DATE_TOKEN IS_OPEN
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

projectStartDirective
    : PROJECT STARTS DATE_TOKEN
    ;

// ========== Tasks ==========
taskGroupDirective
    : DASH (WORD | INTEGER)+ DASH
    ;

task
    : taskBody
    ;

taskGroup
    : DASH (WORD | INTEGER)+ DASH
    ;

taskBody
    : LBRACK taskName RBRACK (AS LBRACK alias=WORD RBRACK)? taskResource? taskTiming? (IS_COMPLETED | partialComplete | IS_COLORED WORD+)*
    ;

partialComplete
    : WORD INTEGER PERCENT WORD
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
    : requiresOrLastsClause startsAtOrDateClause?
    | startsAtOrDateClause requiresOrLastsClause?
    ;

requiresOrLastsClause
    : REQUIRES duration
    | LASTS duration
    ;

startsAtOrDateClause
    : STARTS_AT startDate
    | STARTS DATE_TOKEN
    | ENDS DATE_TOKEN
    ;

duration
    : INTEGER (DAYS | WEEKS)
    ;

startDate
    : DATE_TOKEN
    | taskRef S_END
    | taskRef S_START
    ;

taskRef
    : LBRACK taskName RBRACK
    ;

// ========== Arrow Dependency ==========
arrowDependency
    : taskRef ARROW taskRef (ARROW taskRef)*
    ;

// ========== Then Task ==========
thenTask
    : taskGroup? THEN taskBody
    ;

// ========== Milestone ==========
milestone
    : taskGroup? taskName HAPPENS (AT taskRef S_END | ON DATE_TOKEN)
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
// All keywords and symbols first (longest match, specific before general)
STARTGANTT      : '@startgantt' ;
ENDGANTT        : '@endgantt' ;
SATURDAY_CLOSE  : 'saturday' WS1 'are' WS1 'closed' ;
SUNDAY_CLOSE    : 'sunday' WS1 'are' WS1 'closed' ;
IS_CLOSED       : 'is' WS1 'closed' ;
IS_OPEN         : 'is' WS1 'open' ;
IS_OFF          : 'is' WS1 'off' ;
IS_COMPLETED    : 'is' WS1 'completed' ;
IS_COLORED      : 'is' WS1 'colored' ;
ON              : 'on' ;
REQUIRES        : 'requires' ;
LASTS           : 'lasts' ;
STARTS_AT       : 'starts' WS1 'at' ;
STARTS          : 'starts' ;
ENDS            : 'ends' ;
PRINTSCALE      : 'printscale' ;
TITLE           : 'title' ;
PROJECT         : 'project' ;
WEEKLY          : 'weekly' ;
DAILY           : 'daily' ;
MONTHLY         : 'monthly' ;
DAYS            : 'days' ;
WEEKS           : 'weeks' ;
PERCENT         : '%' ;
COLON           : ':' ;
LBRACE          : '{' ;
RBRACE          : '}' ;
LBRACK          : '[' ;
RBRACK          : ']' ;
DASH            : '--' ;
AS              : 'as' ;
THEN            : 'then' ;
HAPPENS         : 'happens' ;
AT              : 'at' ;
TO              : 'to' ;
ARROW           : '->' ;

DATE_TOKEN
    : DIGIT DIGIT DIGIT DIGIT '-' DIGIT DIGIT '-' DIGIT DIGIT
    ;

INTEGER
    : DIGIT+
    ;

fragment DIGIT : [0-9] ;
fragment WS1   : [ \t]+ ;

// Possessive tokens (must come before LINE_COMMENT)
// These use apostrophe + 's' + whitespace + end/start
S_END   : '\u0027' 's' WS1 'end' ;
S_START : '\u0027' 's' WS1 'start' ;

// Line comment: apostrophe followed by SPACE then anything
// The space requirement prevents conflict with S_END/S_START
LINE_COMMENT
    : '\u0027' ' ' ~[\r\n]* -> skip
    ;

// Block comment
BLOCK_COMMENT
    : '/\u0027' .*? '\u0027/' -> skip
    ;

// General word token (apostrophe excluded to avoid conflict with S_END/S_START)
WORD
    : ~[\] \t\r\n{}()[:%'']+
    ;

NEWLINE
    : [\r\n]+
    -> skip
    ;

WS
    : [ \t]+
    -> skip
    ;
