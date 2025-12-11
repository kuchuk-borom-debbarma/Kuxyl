create a system where i can view the flow of the application at any level.
On the top most zoom level (zoomed out)
Only the important stuffs will be shown similar to traditional tracing such as service calls, database calls, external api calls etc all shown horizontally in timeline span

As we zoom in the we will breakdown the important stuffs into smaller chunks

such as take the example

foo(){
    a();
    b();
    c();
}

a(){
    internal stuff
}

b(){
    d();
    e();
}
c(){
    internal stuff
}

d(){
    internal stuff
}
e(){
    internal stuff
    f(){}
}
f(){
    internal stuff()
    serviceBCalled()
}

This is the codebase we have foo being called as the main code

At high most level we should see foo as the span and serviceCall as its sub span

[ -------------------------- foo() -------------------------- ]
              |
              +---> [ serviceCall ]

as we go one zoom level deeper we should see
[ -------------------------- foo() -------------------------- ]
              |
      [a]        [b]                         [c]
     [         +---> [ serviceCall ].      
              +---> [ serviceCall ]

see carefully how we are zooming in from the high most level slowly into the important stuff


if we zoom in one level more we should see

[ -------------------------- foo() -------------------------- ]
              [a]        [b]                         [c]
                            [d]                      
             [                  +---> [ serviceCall ].---   

you see how we are now seeing d