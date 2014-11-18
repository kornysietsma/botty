(ns botty.core
  (:require [irclj.core :as irclj]
            [clojure.pprint :refer [pprint]]
            [clojure.core.async :refer [chan go-loop >! <! timeout alt! put! <!!] :as async]
            [clojure.string :refer [trim]]
            )
  (:gen-class))

(comment "sample config"
         {:botname "fred"   ; default name, also for commands to bot
          :channels ["#fred"] ; start in these channels - list for future multi-channel ability
          :prefix "fred:"     ; command prefix - default is botname + ':'
          :irchost "localhost"
          :ircport 6667
          :tick-ms 1000     ; how long between ticks
          }
         "sample callbacks"
         {
          :on-tick (fn [world] world)  ; tick alters the world, so be careful!
          :on-command (fn [world {:keys [cmd from-nick reply-to] :as payload}]
                        do-something-sideffecty
                        )
          }
         "sample world"
         {:connection the-irc-connection
          :in in-channel                                    ; where command messages go
          :killer killer-channel                            ; where kill messages go
          :config initial-config-except-initial-state
          :on-tick as-above
          :on-command as-above
          :anything-else you-can-add-stuff-to-the-world-in-on-tick
          }
         )

(defn cmd-prefix [config]
  (or (:prefix config)
      (str (:botname config) ":")))

(defn the-botname [world]
  (get-in world [:config :botname]))

(defn parse-command [{:keys [config] :as world} text target]
  (if (= (:botname config) target)
    (trim text)
    (if-let [match (re-matches (re-pattern (str (cmd-prefix config) "(.*)")) text)]
      (trim (second match))
      nil)))

(defn matches-regexp [matcher text]
  (if matcher
    (if (re-matches matcher text)
      true
      false)
    false))

(defn- privmsg-callback
  [world raw-irc {:keys [nick text target] :as data}]
  ; nick is the sender, target is the channel or botname if private
  (let [botname (the-botname world)
        reply-to (if (= target botname)
                   nick
                   target)]
    (if-let [command (parse-command world text target)]
      (put! (:in world) {:type      :command
                         :value     command
                         :from-nick nick
                         :reply-to  reply-to}))
    (if (matches-regexp (get-in world [:config :matcher]) text)
      (put! (:in world) {:type      :match
                         :value     text
                         :from-nick nick
                         :reply-to  reply-to}))))

(defn send-message! "send a message to a target nick or channel, via current botty world"
  [world target message]
  (irclj/message (:connection world) target message))

(defn broadcast-message! "send a message to all current channels"
  [world message]
  (doseq [channel (get-in world [:config :channels])]
    (send-message! world channel message)))

(defn- irc-tick [world]
  (try
    (let [new-world ((:on-tick world) world)]
      (if (nil? new-world)
        (do (println "Botty error - You need to return a non-nil world from a tick function!!!")
            world)
        new-world))
    (catch Exception e
      (do
        (println "Botty caught exception in tick handler:")
        (clojure.stacktrace/print-stack-trace e)
        world)
      )))

(defn quit! [world reason]
  "shut down the bot - asynchronous so hopefully any farewell commands will go first"
  (put! (:killer world) reason))

(defn- callbacks [world] {:privmsg (partial privmsg-callback world)})

(defn- connection [{:keys [config] :as world}]
  (irclj/connect
    (:irchost config)
    (:ircport config)
    (:botname config)
    :username (:botname config)
    :realname (:botname config)
    :callbacks (callbacks world)
    :auto-reconnect-delay-mins 1 ; reconnect delay after disconnect
    :timeout-mins 20 ; socket timeout - length of time to keep socket open when nothing happens
    ))

(defn default-on-tick [world]
  (broadcast-message! world "tick")
  world)

(defn default-on-command [world {:keys [type value from-nick reply-to] :as payload}]
  (case type
    :command
    (let [botname (the-botname world)]
      (case (clojure.string/trim value)
        "quit" (do
                 (broadcast-message! world (str botname " killed by request from " from-nick))
                 (quit! world (str botname " killed by request from " from-nick)))
        "help" (send-message! world reply-to (str
                                               "send message via '" botname ":cmd' or /msg " botname " 'cmd'\n"
                                               "commands: 'help' and 'quit' only!"))
        (send-message! world reply-to (str "Unknown command:" value " - try 'help'")))
      world)
    :match
    (do
      (broadcast-message! world "You found the secret word!")
      world)))

(def default-callbacks
  {:on-tick default-on-tick
   :on-command default-on-command})

(defn connect [initial-config callbacks in killer]
  (let [world (merge
                {:config initial-config}
                (merge default-callbacks callbacks)
                {:in in
                 :killer killer})
        conn (connection world)
        _ (doseq [channel (:channels initial-config)]
            (irclj/join conn channel))]
    (merge world {:connection conn})))

(defn irc-loop
  "core.async based main loop - returns a killer channel you can put! to to kill the bot, or wait for close to handle death" [initial-config callbacks]
  (let [in (chan)
        killer (chan)]
    (go-loop [world (connect initial-config callbacks in killer)]
      (alt!
        in ([command]
            (recur ((:on-command world) world command)))
        killer ([reason] (do
                           (irclj/quit (:connection world))
                           (println "die:" reason)))
        (timeout (get-in world [:config :tick-ms])) (recur (irc-tick world))))
    killer))


(def local-test-config
  {
   :botname "botty"
   :irchost "localhost"
   :ircport 6667
   :channels ["#general"]
   :tick-ms 10000
   :matcher #".*duck.*"
   })

(comment "for repling"
  (def k (irc-loop local-test-config {}))
  (put! k "die")
)

(defn -main [& args]
  (let [killer (irc-loop local-test-config {})]
    (prn "default test botty running - waiting to die.")
    (<!! killer)
    (prn "done!")))
