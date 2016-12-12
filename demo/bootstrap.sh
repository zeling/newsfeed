tmux new-window
tmux split-window -h
tmux send-keys 'java -Dapplication.node.port=2551 -Dapplication.leveldb.start-shared-leveldb=true -Dapplication.rest.port=8081 -jar target/scala-2.11/news-feed-assembly-1.0.jar' C-m
sleep 1
tmux split-window -v -p 75
tmux send-keys 'java -Dapplication.node.port=2552 -Dapplication.rest.port=8082 -jar target/scala-2.11/news-feed-assembly-1.0.jar' C-m
sleep 1
tmux split-window -v -p 66
tmux send-keys 'java -Dapplication.node.port=0 -Dapplication.rest.port=8083 -jar target/scala-2.11/news-feed-assembly-1.0.jar' C-m
sleep 1
tmux split-window -v -p 50
tmux send-keys 'java -Dapplication.node.port=0 -Dapplication.rest.port=8084 -jar target/scala-2.11/news-feed-assembly-1.0.jar' C-m
sleep 1
tmux select-pane -L
tmux send-keys 'haproxy -f demo/haproxy.cfg -Ds'

