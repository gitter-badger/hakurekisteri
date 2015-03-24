var expect = chai.expect;
chai.should();
chai.config.truncateThreshold = 0; // disable truncating

function Button(el) {
    return {
        element: function () {
            return el()
        },
        isEnabled: function () {
            return !el().prop("disabled")
        },
        isVisible: function () {
            return el().is(":visible")
        },
        click: function () {
            el().click()
        },
        isRealButton: function () {
            return el().prop("tagName") == "BUTTON"
        },
        hasTabIndex: function () {
            return el().prop("tabIndex") > 0
        },
        isFocusableBefore: function (button) {
            return !this.hasTabIndex() && !button.hasTabIndex() && compareDOMIndex(this.element(), button.element()) < 0
        }
    }
}

function S(selector) {
    try {
        if (!testFrame() || !testFrame().jQuery) {
            return $([])
        }
        return testFrame().jQuery(selector)
    } catch (e) {
        console.log("Premature access to testFrame.jQuery, printing stack trace.");
        console.log(new Error().stack);
        throw e;
    }
}

wait = {
    maxWaitMs: testTimeout,
    waitIntervalMs: 30,
    until: function (condition, count) {
        return function () {
            var deferred = Q.defer();
            if (count == undefined) count = wait.maxWaitMs / wait.waitIntervalMs;

            (function waitLoop(remaining) {
                var cond = condition();
                if (cond) {
                    deferred.resolve()
                } else if (remaining < 1) {
                    deferred.reject("timeout of " + wait.maxWaitMs + " in wait.until")
                } else {
                    setTimeout(function () {
                        waitLoop(remaining - 1)
                    }, wait.waitIntervalMs)
                }
            })(count);
            return deferred.promise
        }
    },
    untilFalse: function (condition) {
        return wait.until(function () {
            return !condition()
        })
    },
    forAngular: function () {
        var deferred = Q.defer();
        try {
            var angular = testFrame().angular;
            var el = angular.element(S("body"));
            var timeout = angular.element(el).injector().get('$timeout');
            angular.element(el).injector().get('$browser').notifyWhenNoOutstandingRequests(function () {
                timeout(function () {
                    deferred.resolve()
                })
            })
        } catch (e) {
            deferred.reject(e)
        }
        return deferred.promise
    },
    forMilliseconds: function (ms) {
        return function () {
            var deferred = Q.defer();
            setTimeout(function () {
                deferred.resolve()
            }, ms);
            return deferred.promise
        }
    }
};

uiUtil = {
    inputValues: function (el) {
        function formatKey(key) {
            return key.replace(".data.", ".")
        }

        function getId(el) {
            return [el.attr("ng-model"), el.attr("ng-bind")].join("")
        }

        return _.chain(el.find("[ng-model]:visible, [ng-bind]:visible"))
            .map(function (el) {
                return [formatKey(getId($(el))), $(el).val() + $(el).text()]
            })
            .object().value()
    }
};

mockAjax = {
    init: function () {
        var deferred = Q.defer();
        if (testFrame().sinon)
            deferred.resolve();
        else
            testFrame().$.getScript('test/lib/sinon-server-1.10.3.js', function () {
                deferred.resolve()
            });
        return deferred.promise
    },
    respondOnce: function (method, url, responseCode, responseBody) {
        var fakeAjax = function () {
            var xhr = sinon.useFakeXMLHttpRequest()
            xhr.useFilters = true
            xhr.addFilter(function (method, url) {
                return url != _fakeAjaxParams.url || method != _fakeAjaxParams.method
            });

            xhr.onCreate = function (request) {
                window.setTimeout(function () {
                    if (window._fakeAjaxParams && request.method == _fakeAjaxParams.method && request.url == _fakeAjaxParams.url) {
                        request.respond(_fakeAjaxParams.responseCode, {"Content-Type": "application/json"}, _fakeAjaxParams.responseBody);
                        xhr.restore();
                        delete _fakeAjaxParams;
                    }
                }, 0)
            }
        };

        testFrame()._fakeAjaxParams = {
            method: method,
            url: url,
            responseCode: responseCode,
            responseBody: responseBody
        };
        testFrame().eval("(" + fakeAjax.toString() + ")()")
    }
};

util = {
    flattenObject: function (obj) {
        function flatten(obj, prefix, result) {
            _.each(obj, function (val, id) {
                if (_.isObject(val)) {
                    flatten(val, id + ".", result)
                } else {
                    result[prefix + id] = val
                }
            });
            return result
        }

        return flatten(obj, "", {})
    }
};

function getJson(url) {
    return Q($.ajax({url: url, dataType: "json"}))
}

function testFrame() {
    return $("#testframe").get(0).contentWindow
}

function openPage(path, predicate) {
    if (!predicate) {
        console.log("nopredicate for: " + path);
        predicate = function () {
            return testFrame().jQuery
        }
    }
    return function () {
        var newTestFrame = $('<iframe/>').attr({src: path, width: 1024, height: 800, id: "testframe"});
        $("#testframe").replaceWith(newTestFrame);
        return wait.until(function () {
            testFrame().mocksOn = true
            return predicate()
        })().then(function () {
            window.uiError = null;
            testFrame().onerror = function (err) {
                window.uiError = err;
            }; // Hack: force mocha to fail on unhandled exceptions
        })
    }
}

function exists(fn) {
    if (typeof(fn) !== 'function') {
        throw new Error('exists() got a non-function');
    }
    return wait.until(function() {
        return fn().length > 0;
    })
}

function autocomplete(inputFn, text, selectItemFn) {
    return seq(
        visible(inputFn),
        function() { return inputFn().val(text).change()},
        click(selectItemFn)
    )
}

function takeScreenshot() {
    if (window.callPhantom) {
        var date = new Date();
        var filename = "target/screenshots/" + date.getTime();
        console.log("Taking screenshot " + filename);
        callPhantom({'screenshot': filename});
    } else {
        console.error('No screenshot saved')
    }
}

(function improveMocha() {
    var origBefore = before
    before = function () {
        Array.prototype.slice.call(arguments).forEach(function (arg) {
            if (typeof arg !== "function") {
                throw ("not a function: " + arg)
            }
            origBefore(arg)
        })
    }
})();